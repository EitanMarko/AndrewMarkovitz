#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
test_build_dir="$(mktemp -d "${TMPDIR:-/tmp}/productivime-tests.XXXXXX")"
trap 'rm -rf "$test_build_dir"' EXIT
xcrun swiftc -swift-version 5 -parse-as-library \
  -module-cache-path "$test_build_dir/ModuleCache" \
  ProductiviMe/Engine/*.swift Tests/RegressionTests.swift \
  -o "$test_build_dir/regression-tests"
"$test_build_dir/regression-tests"
