#!/usr/bin/env sh
set -eu

# Compila y ejecuta el Backend. El puerto puede pasarse como primer argumento.
cd "$(dirname "$0")/.."
PORT="${1:-8082}"

rm -rf out-backend
mkdir -p out-backend

javac -d out-backend \
    backend/src/main/java/com/fase1/calculadora/CalculatorServer.java \
    backend/src/main/java/com/fase1/calculadora/CalculatorService.java \
    backend/src/main/java/com/fase1/calculadora/HistoryRepository.java

java --add-modules jdk.httpserver -cp out-backend \
    com.fase1.calculadora.CalculatorServer "$PORT"
