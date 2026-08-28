#!/usr/bin/env bash
set -euo pipefail

BACKEND_URL="${BACKEND_URL:-http://localhost:8082}"
FRONTEND_URL="${FRONTEND_URL:-http://localhost:8081}"

fail() {
    echo "ERROR: $1" >&2
    exit 1
}

wait_for() {
    local url="$1"
    local name="$2"
    local attempt

    for attempt in {1..30}; do
        if curl -fsS "$url" >/dev/null 2>&1; then
            return 0
        fi
        sleep 1
    done

    fail "$name no respondió en $url"
}

post_ok() {
    local endpoint="$1"
    local payload="$2"
    local expected="$3"
    local response
    local body
    local status

    response="$(curl -sS -w $'\n%{http_code}' -H 'Content-Type: application/json' -d "$payload" "$BACKEND_URL$endpoint")"
    body="${response%$'\n'*}"
    status="${response##*$'\n'}"

    [ "$status" = "200" ] || fail "$endpoint devolvió HTTP $status"
    printf '%s' "$body" | grep -Fq "$expected" || fail "$endpoint no devolvió $expected"
}

wait_for "$BACKEND_URL/health" "Backend"
wait_for "$FRONTEND_URL/status" "Frontend"

post_ok "/api/sum" '{"a":2,"b":3}' '"result":5'
post_ok "/api/subtract" '{"a":10,"b":4}' '"result":6'
post_ok "/api/multiply" '{"a":2.5,"b":4}' '"result":10'
post_ok "/api/divide" '{"a":8,"b":2}' '"result":4'

response="$(curl -sS -w $'\n%{http_code}' -H 'Content-Type: application/json' -d '{"a":1,"b":0}' "$BACKEND_URL/api/divide")"
body="${response%$'\n'*}"
status="${response##*$'\n'}"
[ "$status" = "400" ] || fail "/api/divide entre cero devolvió HTTP $status"
printf '%s' "$body" | grep -Fq 'No se puede dividir entre cero' || fail "La división entre cero no devolvió el mensaje esperado"

response="$(curl -sS -w $'\n%{http_code}' "$BACKEND_URL/api/history")"
body="${response%$'\n'*}"
status="${response##*$'\n'}"
[ "$status" = "200" ] || fail "/api/history devolvió HTTP $status"
printf '%s' "$body" | grep -Fq '"operation"' || fail "/api/history no devolvió operaciones"

response="$(curl -sS -w $'\n%{http_code}' "$BACKEND_URL/health")"
body="${response%$'\n'*}"
status="${response##*$'\n'}"
[ "$status" = "200" ] || fail "/health devolvió HTTP $status"
printf '%s' "$body" | grep -Fq '"status":"UP"' || fail "/health no reportó estado UP"
printf '%s' "$body" | grep -Fq '"uptimeSeconds":' || fail "/health no devolvió uptime"
printf '%s' "$body" | grep -Fq '"persistenceWritable":true' || fail "/health no confirmó persistencia escribible"

response="$(curl -sS -w $'\n%{http_code}' "$FRONTEND_URL/status")"
body="${response%$'\n'*}"
status="${response##*$'\n'}"
[ "$status" = "200" ] || fail "/status devolvió HTTP $status"
printf '%s' "$body" | grep -Fq '"status":"UP"' || fail "/status no reportó estado UP"
printf '%s' "$body" | grep -Fq '"backendStatus":"UP"' || fail "/status no reportó el Backend como UP"
printf '%s' "$body" | grep -Fq '"persistenceWritable":true' || fail "/status no confirmó persistencia escribible"

echo "OK - Endpoints HU1-HU5 validados"
