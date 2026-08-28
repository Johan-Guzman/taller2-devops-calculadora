#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
rm -rf out-backend
mkdir -p out-backend
javac -d out-backend backend/src/main/java/com/fase1/calculadora/CalculatorServer.java backend/src/main/java/com/fase1/calculadora/CalculatorService.java backend/src/main/java/com/fase1/calculadora/HistoryRepository.java
java --add-modules jdk.httpserver -cp out-backend com.fase1.calculadora.CalculatorServer "${1:-8080}"