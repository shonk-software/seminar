#!/usr/bin/env bash
set -euo pipefail

cd libquicksort
cargo clean

cd ../ffm-java-side
mvn clean
