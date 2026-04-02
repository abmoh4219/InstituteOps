#!/usr/bin/env bash

set -euo pipefail

echo "[run_test.sh] Starting integration test suite..."
./mvnw -B verify -Pintegration-tests
echo "[run_test.sh] Integration test suite completed successfully."
