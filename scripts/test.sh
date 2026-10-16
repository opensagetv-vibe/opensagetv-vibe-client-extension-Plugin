#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
bash "$root/scripts/build.sh"
stock_jar="${SAGETV_JAR:-$root/.deps/stock/Sage.jar}"
mkdir -p "$root/build/test-classes"
mapfile -d '' test_sources < <(find "$root/src/test/java" -type f -name '*.java' -print0)
javac --release 8 -cp "$root/build/classes:$stock_jar" -d "$root/build/test-classes" "${test_sources[@]}"
java -cp "$root/build/test-classes:$root/build/classes:$stock_jar" \
  org.opensagetv.vibe.clientextension.ConfigContractTest
java -cp "$root/build/test-classes:$root/build/classes:$stock_jar" \
  org.opensagetv.vibe.clientextension.DiagnosticStoreTest
# Compile production only against stock Core above. Shadow the player boundary
# solely in this isolated JVM to deterministically inject seek/free races and
# the malformed-index exception; these are not physical disc acceptance gates.
mkdir -p "$root/build/recovery-test-classes"
mapfile -d '' recovery_sources < <(find "$root/src/test/recovery-java" -type f -name '*.java' -print0)
javac --release 8 -cp "$root/build/test-classes:$root/build/classes:$stock_jar" \
  -d "$root/build/recovery-test-classes" "${recovery_sources[@]}"
java -cp "$root/build/recovery-test-classes:$root/build/test-classes:$root/build/classes:$stock_jar" \
  org.opensagetv.vibe.clientextension.RecoverySafetyTest
jar tf "$root/dist/dev/VibeClientExtensionPlugin.jar" |
  grep -Fxq 'org/opensagetv/vibe/clientextension/ClientExtensionDvdPlayer.class'
jar tf "$root/dist/dev/VibeClientExtensionPlugin.jar" |
  grep -Fxq 'org/opensagetv/vibe/clientextension/ClientExtensionPlugin.class'
if jar tf "$root/dist/dev/VibeClientExtensionPlugin.jar" | grep -Eq '^sage/'; then
  echo 'Plugin JAR must not bundle SageTV Core or test fakes.' >&2
  exit 3
fi
if grep -R -E 'ProcessBuilder|Runtime\.getRuntime\(\)\.exec|ffmpeg_MIM|ffmpeg\.real\.ini' \
    "$root/src/main/java"; then
  echo 'The companion must use the separate FFmpeg/MIM provider.' >&2
  exit 3
fi
echo 'PASS: stock player-hook and FFmpeg separation contracts'
if jar tf "$root/dist/dev/VibeClientExtensionPlugin.jar" | grep -q 'DiagnosticHttpServer'; then
  echo 'The Android diagnostic web server must not be bundled with Client Extension.' >&2
  exit 3
fi
