#!/usr/bin/env bash
set -euo pipefail

# Despliegue remoto por SSH: transfiere docker-compose.yml y el código
# fuente necesario hacia el equipo destino
# y levanta allí los contenedores con docker compose.
#
# Se ejecuta desde el PC Ops de este equipo (Windows, vía Git Bash) hacia
# el PC Ops del equipo par (Linux). Usa scp en lugar de rsync porque no
# se puede asumir que rsync esté disponible en el lado Windows.
#
# Variables requeridas:
#   REMOTE_HOST  IP o hostname del equipo destino
#   REMOTE_USER  usuario SSH en el equipo destino
#   REMOTE_DIR   directorio destino donde se copia el proyecto
# Variables opcionales:
#   SSH_KEY      ruta a la llave privada SSH a usar
#   SSH_PORT     puerto SSH (por defecto 22)

fail() {
    echo "ERROR: $1" >&2
    exit 1
}

: "${REMOTE_HOST:?debe definirse REMOTE_HOST (IP del equipo destino)}"
: "${REMOTE_USER:?debe definirse REMOTE_USER (usuario SSH del equipo destino)}"
: "${REMOTE_DIR:?debe definirse REMOTE_DIR (directorio destino en el equipo remoto)}"
SSH_PORT="${SSH_PORT:-22}"

command -v ssh >/dev/null 2>&1 || fail "ssh no está instalado"
command -v scp >/dev/null 2>&1 || fail "scp no está instalado"

# ssh usa -p para el puerto, scp usa -P (mayúscula); por eso van en arrays separados.
SSH_OPTS=(-p "$SSH_PORT" -o StrictHostKeyChecking=accept-new -o BatchMode=yes)
SCP_OPTS=(-P "$SSH_PORT" -o StrictHostKeyChecking=accept-new -o BatchMode=yes)
if [ -n "${SSH_KEY:-}" ]; then
    [ -f "$SSH_KEY" ] || fail "no se encontró la llave SSH en $SSH_KEY"
    SSH_OPTS+=(-i "$SSH_KEY")
    SCP_OPTS+=(-i "$SSH_KEY")
fi

cd "$(dirname "$0")/.."

echo "Verificando conexión SSH con ${REMOTE_USER}@${REMOTE_HOST}:${SSH_PORT}..."
ssh "${SSH_OPTS[@]}" "${REMOTE_USER}@${REMOTE_HOST}" "mkdir -p '${REMOTE_DIR}'" \
    || fail "no se pudo conectar o crear ${REMOTE_DIR} en el equipo remoto"

echo "Transfiriendo docker-compose.yml, Dockerfiles y código fuente..."
scp "${SCP_OPTS[@]}" -r \
    docker-compose.yml Dockerfile.backend Dockerfile.frontend backend frontend \
    "${REMOTE_USER}@${REMOTE_HOST}:${REMOTE_DIR}/" \
    || fail "falló la transferencia con scp"

echo "Levantando contenedores en el equipo remoto..."
ssh "${SSH_OPTS[@]}" "${REMOTE_USER}@${REMOTE_HOST}" \
    "cd '${REMOTE_DIR}' && docker compose up -d --build" \
    || fail "falló 'docker compose up -d' en el equipo remoto"

echo "OK - Despliegue remoto completado en ${REMOTE_HOST}:${REMOTE_DIR}"
