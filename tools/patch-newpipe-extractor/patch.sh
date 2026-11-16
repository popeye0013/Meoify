#!/usr/bin/env bash
# Rebuilds app/libs/NewPipeExtractor-v0.27.1-patched.jar from the official
# Maven artifact. Only needed when changing NewPipeExtractor versions.
set -euo pipefail
cd "$(dirname "$0")"

ASM_JAR=$(ls /opt/gradle-8.14.3/lib/asm-[0-9]*.jar | head -1)
ORIGINAL=${1:-$(find "$HOME/.gradle/caches/modules-2/files-2.1/com.github.TeamNewPipe/NewPipeExtractor/v0.27.1" -name 'NewPipeExtractor-v0.27.1.jar' | head -1)}
OUTPUT=${2:-../../app/libs/NewPipeExtractor-v0.27.1-patched.jar}

if [ -z "$ORIGINAL" ] || [ ! -f "$ORIGINAL" ]; then
  echo "Original NewPipeExtractor jar not found; pass it as the first argument." >&2
  exit 1
fi

rm -rf .classes
mkdir .classes
javac -cp "$ASM_JAR" -d .classes PatchNewPipe.java
java -cp "$ASM_JAR:.classes" PatchNewPipe "$ORIGINAL" "$OUTPUT"
rm -rf .classes
