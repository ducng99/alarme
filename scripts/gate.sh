#!/usr/bin/env bash
# CI-style quality gate per milestone (plan §5).
# Usage: scripts/gate.sh [extra gradle args...]
set -euo pipefail
cd "$(dirname "$0")/.."

./gradlew \
    ktfmtCheck \
    detekt \
    lintDebug \
    testDebugUnitTest \
    "$@"
