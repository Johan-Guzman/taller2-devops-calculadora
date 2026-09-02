#!/usr/bin/env sh
set -eu

# Compila y ejecuta el Frontend apuntando al Backend indicado.
cd "$(dirname "$0")/.."

if [ "$#" -lt 1 ]; then
    echo "Uso: ./scripts/run-frontend.sh http://IP_BACKEND:PUERTO_BACKEND [PUERTO_FRONTEND]" >&2
    exit 1
fi

BACKEND_URL="$1"
PORT="${2:-8081}"

rm -rf out-frontend
mkdir -p out-frontend
javac -d out-frontend backend/src/main/java/com/fase1/calculadora/FrontendServer.java

java --add-modules jdk.httpserver -cp out-frontend \
    com.fase1.calculadora.FrontendServer "$BACKEND_URL" "$PORT"
