#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
output=".build/core-checks"
mkdir -p "$output"
sources=(camera-core/src/main/java/com/sturgeon/xflipcamera/core/*.java
         camera-core/src/test/java/com/sturgeon/xflipcamera/core/*.java)
if command -v javac >/dev/null 2>&1; then
    javac --release 17 -Xlint:all -Werror -d "$output" "${sources[@]}"
else
    java -m jdk.compiler/com.sun.tools.javac.Main \
        --release 17 -Xlint:all -Werror -d "$output" "${sources[@]}"
fi
java -cp "$output" com.sturgeon.xflipcamera.core.CoreChecks
