#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
for name in AGENTS.md README.md WORKFLOW.md TASKS.md HANDOFF.md CHANGELOG.md \
            CONTRIBUTING.md SECURITY.md THIRD_PARTY_NOTICES.md VERSION \
            release.properties dev.sh dev.cmd dev.ps1 .gitattributes \
            .github/workflows/repository-checks.yml; do
  test -f "$root/$name"
done
test "$(tr -d '\r\n' < "$root/VERSION")" = \
  "$(sed -n 's/^VERSION=//p' "$root/release.properties" | tr -d '\r')"
test -f "$root/artifacts/downloads/.gitkeep"
python3 "$root/scripts/validate_manifest.py"
if find "$root" -type f -name 'Sage.jar' -not -path "$root/.deps/*" -print -quit | grep -q .; then
  echo 'Stock Sage.jar must be a compile-only private dependency.' >&2
  exit 3
fi
echo 'PASS: Vibe project source contracts'
