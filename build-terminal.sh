#!/bin/sh
# Build from terminal using a project-local Gradle home to avoid
# "Failed to create Jar file" in ~/.gradle/caches (permission/path issues).
# Usage: ./build-terminal.sh [tasks...]   e.g. ./build-terminal.sh assembleDebug
set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"
export GRADLE_USER_HOME="$SCRIPT_DIR/.gradle-terminal"
exec ./gradlew "$@"
