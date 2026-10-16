#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
command="${1:-help}"
build_env="${OPENSAGETV_VIBE_BUILD_ENV_ROOT:-$root/../opensagetv-vibe-build-env}"
case "$command" in
  build|test|validate|package|all|install)
    exec bash "$build_env/scripts/component-dev.sh" "$root" "$command"
    ;;
  help|-h|--help) echo 'Usage: ./dev.sh {test|validate|build|package|install|all}' ;;
  *) echo "Unknown command: $command" >&2; exit 2 ;;
esac
