#!/usr/bin/env bash
set -euo pipefail


cd libquicksort
cargo build --release

cd ../ffm-java-side
mvn compile exec:java@weather
mvn compile exec:java@sorter
