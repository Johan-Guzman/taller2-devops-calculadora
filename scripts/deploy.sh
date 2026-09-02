#!/usr/bin/env bash
set -euo pipefail

# Despliega el proyecto en el PC Ops del equipo par mediante SSH y Docker Compose.
# Los puertos remotos son configurables para evitar colisiones entre equipos.

fail() {
    echo "ERROR: $1" >&2
    exit 1
}

: "${REMOTE_HOST:?debe definirse REMOTE_HOST}"
: "${REMOTE_USER:?debe definirse REMOTE_USER}"
: "${REMOTE_DIR:?debe definirse REMOTE_DIR}"

SSH_PORT="${SSH_PORT:-22}"
REMOTE_BACKEND_PORT="${REMOTE_BACKEND_PORT:-8084}"
REMOTE_FRONTEND_PORT="${REMOTE_FRONTEND_PORT:-8083}"
REMOTE_PROJECT_NAME="${REMOTE_PROJECT_NAME:-calculadora-peer}"

valid_port() {
    local value="$1"
    [[ "$value" =~ ^[0-9]+$ ]] && (( value >= 1 && value <= 65535 ))
}

valid_port "$SSH_PORT" || fail "SSH_PORT debe estar entre 1 y 65535"
valid_port "$REMOTE_BACKEND_PORT" || fail "REMOTE_BACKEND_PORT debe estar entre 1 y 65535"
valid_port "$REMOTE_FRONTEND_PORT" || fail "REMOTE_FRONTEND_PORT debe estar entre 1 y 65535"
[ "$REMOTE_BACKEND_PORT" != "$REMOTE_FRONTEND_PORT" ] || fail "Backend y Frontend no pueden usar el mismo puerto"
[[ "$REMOTE_PROJECT_NAME" =~ ^[A-Za-z0-9][A-Za-z0-9_.-]*$ ]] || fail "REMOTE_PROJECT_NAME contiene caracteres no válidos"

command -v ssh >/dev/null 2>&1 || fail "ssh no está instalado"
command -v scp >/dev/null 2>&1 || fail "scp no está instalado"
command -v curl >/dev/null 2>&1 || fail "curl no está instalado"

SSH_OPTS=(-p "$SSH_PORT" -o StrictHostKeyChecking=accept-new -o BatchMode=yes)
SCP_OPTS=(-P "$SSH_PORT" -o StrictHostKeyChecking=accept-new -o BatchMode=yes)
if [ -n "${SSH_KEY:-}" ]; then
    if [ ! -f "$SSH_KEY" ] && command -v cygpath >/dev/null 2>&1; then
        SSH_KEY="$(cygpath -u "$SSH_KEY")"
    fi
    [ -f "$SSH_KEY" ] || fail "no se encontró la llave SSH en $SSH_KEY"
    SSH_OPTS+=(-i "$SSH_KEY")
    SCP_OPTS+=(-i "$SSH_KEY")
fi

cd "$(dirname "$0")/.."

echo "Verificando SSH con ${REMOTE_USER}@${REMOTE_HOST}:${SSH_PORT}..."
ssh "${SSH_OPTS[@]}" "${REMOTE_USER}@${REMOTE_HOST}" \
    "mkdir -p '${REMOTE_DIR}' && rm -rf '${REMOTE_DIR}/backend' '${REMOTE_DIR}/frontend' '${REMOTE_DIR}/docker-compose.yml' '${REMOTE_DIR}/Dockerfile.backend' '${REMOTE_DIR}/Dockerfile.frontend' '${REMOTE_DIR}/.dockerignore'" \
    || fail "no se pudo preparar el directorio remoto"

echo "Transfiriendo artefactos..."
scp "${SCP_OPTS[@]}" -r \
    .dockerignore docker-compose.yml Dockerfile.backend Dockerfile.frontend backend frontend \
    "${REMOTE_USER}@${REMOTE_HOST}:${REMOTE_DIR}/" \
    || fail "falló la transferencia con scp"

echo "Levantando contenedores remotos..."
ssh "${SSH_OPTS[@]}" "${REMOTE_USER}@${REMOTE_HOST}" \
    "cd '${REMOTE_DIR}' && COMPOSE_PROJECT_NAME='${REMOTE_PROJECT_NAME}' BACKEND_PORT='${REMOTE_BACKEND_PORT}' FRONTEND_PORT='${REMOTE_FRONTEND_PORT}' docker compose up -d --build --remove-orphans" \
    || fail "falló docker compose en el equipo remoto"

echo "Validando servicios remotos..."
for attempt in {1..30}; do
    if curl -fsS "http://${REMOTE_HOST}:${REMOTE_BACKEND_PORT}/health" >/dev/null 2>&1 \
        && curl -fsS "http://${REMOTE_HOST}:${REMOTE_FRONTEND_PORT}/status" >/dev/null 2>&1; then
        echo "OK - Despliegue remoto: http://${REMOTE_HOST}:${REMOTE_FRONTEND_PORT}"
        exit 0
    fi
    sleep 1
done

fail "los contenedores arrancaron, pero los endpoints remotos no son alcanzables"
