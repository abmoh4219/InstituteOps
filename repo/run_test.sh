#!/usr/bin/env bash

set -euo pipefail

JAVAC_BIN="$(command -v javac)"
JAVA_HOME="$(dirname "$(dirname "$(readlink -f "${JAVAC_BIN}")")")"
export JAVA_HOME
export PATH="${JAVA_HOME}/bin:${PATH}"

echo "[run_test.sh] Toolchain check"
java -version
javac -version
mvn -version

if ! javac -version 2>&1 | grep -q "javac 21"; then
  echo "[run_test.sh] ERROR: JDK 21 is required for Docker tests." >&2
  exit 1
fi

echo "[run_test.sh] Starting integration test suite..."
mvn -B verify -Pintegration-tests
echo "[run_test.sh] Integration test suite completed successfully."
