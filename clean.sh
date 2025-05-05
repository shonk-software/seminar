#!/usr/bin/env bash
set -euo pipefail

cd libffmexample
cargo clean

cd ../ffm-java-side
mvn clean
