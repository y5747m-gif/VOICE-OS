#!/usr/bin/env bash
# Requires kotlinc 2.1.x, a distribution with kotlinx-coroutines-core-jvm.jar, and Java 17.
set -euo pipefail
cd "$(dirname "$0")/.."
compiler="$(command -v "${KOTLINC:-kotlinc}")"
lib="${KOTLIN_LIB:-$(cd "$(dirname "$(readlink -f "$compiler")")/../lib" && pwd)}"
coroutines="$lib/kotlinx-coroutines-core-jvm.jar"
if [[ ! -f "$coroutines" ]]; then
  echo 'Set KOTLIN_LIB to the Kotlin compiler distribution lib directory.' >&2
  exit 1
fi
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
"$compiler" core/src/main/kotlin core/src/test/kotlin/ai/voiceos/core/{ContractChecks,ReleaseChecks}.kt -jvm-target 17 -cp "$coroutines" -include-runtime -d "$work/contracts.jar"
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp "$work/contracts.jar:$coroutines" ai.voiceos.core.ContractChecksKt
