#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
stock_jar="${SAGETV_JAR:-$root/.deps/stock/Sage.jar}"
if [[ ! -f "$stock_jar" ]]; then
  echo 'An unmodified stock Sage.jar is required in .deps/stock or SAGETV_JAR.' >&2
  exit 2
fi
mkdir -p "$root/build/classes" "$root/dist/dev"
find "$root/build/classes" -type f -name '*.class' -delete
mapfile -d '' sources < <(find "$root/src/main/java" -type f -name '*.java' -print0)
javac --release 8 -cp "$stock_jar" -d "$root/build/classes" "${sources[@]}"
python3 "$root/scripts/build_jar.py"
if jar tf "$root/dist/dev/VibeClientExtensionPlugin.jar" | grep -q '^sage/'; then
  echo 'SageTV Core classes leaked into plugin JAR.' >&2
  exit 3
fi
echo "PASS: built $root/dist/dev/VibeClientExtensionPlugin.jar"
