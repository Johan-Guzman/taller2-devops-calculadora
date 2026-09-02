#!/usr/bin/env sh
set -eu

# Ejecuta las pruebas Java de HU1-HU5 sin depender de Maven o Gradle.
cd "$(dirname "$0")/.."

rm -rf out-test
mkdir -p out-test

javac -d out-test \
    backend/src/main/java/com/fase1/calculadora/*.java \
    backend/src/test/java/com/fase1/calculadora/AppTest.java

java --add-modules jdk.httpserver -cp out-test com.fase1.calculadora.AppTest
