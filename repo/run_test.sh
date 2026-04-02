#!/usr/bin/env bash

set -euo pipefail

print_toolchain_info() {
  local context="$1"
  echo "[run_test.sh][${context}] Toolchain diagnostics"
  if command -v java >/dev/null 2>&1; then
    java -version
  else
    echo "[run_test.sh][${context}] java: not found"
  fi
  if command -v javac >/dev/null 2>&1; then
    javac -version
  else
    echo "[run_test.sh][${context}] javac: not found"
  fi
  if command -v mvn >/dev/null 2>&1; then
    mvn -version
  else
    echo "[run_test.sh][${context}] mvn: not found"
  fi
}

if [[ "${INSTITUTEOPS_TESTS_CONTAINER:-0}" != "1" ]]; then
  print_toolchain_info "host"

  if [[ "${RUN_TEST_SH_DELEGATED:-0}" == "1" ]]; then
    echo "[run_test.sh][host] ERROR: delegation recursion guard triggered." >&2
    exit 1
  fi

  echo "[run_test.sh][host] Delegating test execution to Docker tests service (Java 21)."
  exec env RUN_TEST_SH_DELEGATED=1 docker compose up --build --abort-on-container-exit --exit-code-from tests
fi

JAVAC_BIN="$(command -v javac)"
JAVA_HOME="$(dirname "$(dirname "$(readlink -f "${JAVAC_BIN}")")")"
export JAVA_HOME
export PATH="${JAVA_HOME}/bin:${PATH}"

print_toolchain_info "container"

if ! javac -version 2>&1 | grep -q "javac 21"; then
  echo "[run_test.sh][container] ERROR: JDK 21 is required in Docker tests container." >&2
  exit 1
fi

echo "[run_test.sh][container] Starting integration test suite..."
mvn -B verify -Pintegration-tests
echo "[run_test.sh][container] Integration test suite completed successfully."
