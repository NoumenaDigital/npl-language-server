#!/bin/bash
set -e

cd "$(dirname "$0")"

echo "Generating native image configurations..."
mvn clean verify -Pconfig-gen
