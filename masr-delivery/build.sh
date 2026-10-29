#!/bin/sh
# Compile main + test sources into out/ and testout/ (works with a full JDK, or a JRE that ships jdk.compiler).
set -e
cd "$(dirname "$0")"
JAVAC="javac"
command -v javac >/dev/null 2>&1 || JAVAC="java -m jdk.compiler/com.sun.tools.javac.Main"
rm -rf out testout && mkdir out testout
$JAVAC -Xlint:all -d out $(find src/main -name '*.java')
$JAVAC -cp out -d testout $(find src/test -name '*.java')
echo "Build OK"
