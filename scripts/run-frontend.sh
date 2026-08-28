#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
if [ "$#" -lt 1 ]; then
    echo "Uso: ./run-frontend.sh http://IP_BACKEND:PUERTO_BACKEND [PUERTO_FRONTEND]"
    exit 1
fi
rm -rf out-frontend
mkdir -p out-frontend
javac -d out-frontend backend/src/main/java/com/fase1/calculadora/FrontendServer.java
java --add-modules jdk.httpserver -cp out-frontend com.fase1.calculadora.FrontendServer "$1" "${2:-8081}"