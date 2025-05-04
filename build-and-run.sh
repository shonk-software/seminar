#!/usr/bin/env bash
set -euo pipefail

echo "Building and running the project..."

echo "Building the Rust library..."
printf "\n"
cd libffmexample
cargo build --release

cd ../ffm-java-side
echo "Building and running weather..."
printf "\n"
mvn compile exec:java@weather

printf "\n\n\n\n"

echo "Building and running sorter..."
printf "\n"
mvn compile exec:java@sorter
