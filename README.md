# Calculadora Distribuida Java — Taller 2 y Taller 3 
# Ingesoft 5
## Integrantes

1. Johan-Guzman
2. RenzoFernando
3. Santiago-VH
4. LunaKtalina
5. AdriMatinez

## Taller 2

| HU | Endpoint |
|---|---|
| HU1 — Suma | `POST /api/sum` |
| HU2 — Resta | `POST /api/subtract` |
| HU2 — Multiplicación | `POST /api/multiply` |
| HU3 — Historial | `GET /api/history` — últimas 5 operaciones |
| HU4 — División | `POST /api/divide` — división por cero: HTTP 400 |
| HU5 — Health Backend | `GET /health` |
| HU5 — Status Frontend | `GET /status` |

### Backend

Windows:

```bat
scripts\run-backend.bat 8082
```

Linux:

```bash
./scripts/run-backend.sh 8082
```

### Frontend

Windows:

```bat
scripts\run-frontend.bat http://IP_BACKEND:8082 8081
```

Linux:

```bash
./scripts/run-frontend.sh http://IP_BACKEND:8082 8081
```

Frontend:

```text
http://localhost:8081
```

Backend:

```text
http://localhost:8082
```

### Tests

Windows:

```bat
scripts\test.bat
```

Linux:

```bash
./scripts/test.sh
```

## Taller 3

- Docker y Docker Compose.
- Integración continua con GitHub Actions.
- Runner self-hosted.
- Despliegue remoto por SSH con `deploy.sh`.
- Verificación de `/health` y `/status`.

### Levantar Docker

```bash
docker compose build
docker compose up -d
docker compose ps
```

Logs:

```bash
docker compose logs -f
```

Detener:

```bash
docker compose down
```

### Validar endpoints

```bash
./scripts/test_endpoints.sh
```

### CI

Workflow:

```text
.github/workflows/ci.yml
```

Se ejecuta con `push` a `main` y realiza construcción y pruebas.

### Runner self-hosted

GitHub:

```text
Settings → Actions → Runners → New self-hosted runner
```

Etiqueta usada por el proyecto:

```text
calculadora-ops
```

En Windows, iniciar el runner desde su carpeta:

```powershell
.\run.cmd
```

### CD y SSH

Workflow:

```text
.github/workflows/deploy-self-hosted.yml
```

Script:

```text
scripts/deploy.sh
```

El runner ejecuta el despliegue y `deploy.sh` entrega la aplicación al PC Ops destino por SSH usando Docker Compose.

Secrets usados:

```text
PEER_HOST
PEER_SSH_USER
PEER_DEPLOY_DIR
PEER_SSH_KEY_PATH
PEER_SSH_PORT
PEER_FRONTEND_PORT
PEER_BACKEND_PORT
PEER_COMPOSE_PROJECT_NAME
```

### Verificación

Backend:

```bash
curl http://IP_BACKEND:PUERTO_BACKEND/health
```

Frontend:

```bash
curl http://IP_FRONTEND:PUERTO_FRONTEND/status
```
