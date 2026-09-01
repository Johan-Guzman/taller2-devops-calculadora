# Calculadora Distribuida Java

Aplicación web distribuida con Frontend y Backend separados. El Frontend hace peticiones HTTP/REST al Backend, que concentra la lógica de negocio (suma, resta, multiplicación, división), el historial persistente y el estado de salud del servicio.

**Requisitos:** JDK 17+ (`java`/`javac` en el PATH), o Docker + Docker Compose para el despliegue contenerizado. Ver [requisitos.txt](requisitos.txt).

---

## Despliegue con Docker (recomendado)

```bash
docker compose up -d --build
```

- Backend: `http://localhost:8082`
- Frontend: `http://localhost:8081`
- El historial persiste en un volumen (`history-data`), sobrevive a `docker compose down` sin `-v`.
- El Frontend resuelve al Backend por nombre de servicio (`backend`) dentro de la red interna definida en [docker-compose.yml](docker-compose.yml).

Para bajar los contenedores: `docker compose down` (agrega `-v` para borrar también el historial).

## Ejecución sin Docker (dos equipos)

1. **Backend:** `./scripts/run-backend.sh 8082` (Windows: `scripts\run-backend.bat 8082`)
2. **Frontend:** `./scripts/run-frontend.sh http://<IP_BACKEND>:8082 8081` (Windows: `scripts\run-frontend.bat ...`)

No uses `localhost`/`127.0.0.1` como IP del Backend si Frontend y Backend están en equipos distintos. Abre `http://localhost:8081` en el navegador del equipo Frontend.

## Pruebas

```bash
./scripts/test.sh              # pruebas unitarias/integración Java
./scripts/test_endpoints.sh    # valida los endpoints con el servicio ya arriba (Docker o local)
```

---

## Endpoints del Backend

| Método | Endpoint | Descripción |
|---|---|---|
| POST | `/api/sum` | Suma: `{"a":2,"b":3}` |
| POST | `/api/subtract` | Resta: `{"a":10,"b":4}` |
| POST | `/api/multiply` | Multiplicación: `{"a":2.5,"b":4}` |
| POST | `/api/divide` | División: `{"a":8,"b":2}`. División entre cero devuelve HTTP 400. |
| GET | `/api/history` | Últimas 5 operaciones exitosas |
| GET | `/health` | Estado del Backend, uptime y permisos de persistencia |

El Frontend expone `GET /status` con el mismo tipo de información, incluyendo el estado del Backend.

## Persistencia

Cada operación exitosa se guarda como una línea JSON en `data/history.jsonl` (o en el volumen Docker `history-data`). No elimines este archivo/volumen si quieres conservar el historial entre reinicios.

---

## CI/CD (Taller 3)

Nuestro PC Ops corre **Windows**; el equipo par al que desplegamos corre **Linux**. Esto es asimétrico: el mismo PC Ops también debe poder *recibir* el despliegue entrante del equipo con el que nos emparejen, así que necesita el servidor OpenSSH de Windows habilitado (`Add-WindowsCapability -Online -Name OpenSSH.Server~~~~0.0.1.0`, servicio `sshd` iniciado) además del cliente.

- **CI** ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)): en cada push a `main`, construye las imágenes y corre las pruebas (unitarias + endpoints) en un runner de GitHub.
- **CD** ([`.github/workflows/deploy-self-hosted.yml`](.github/workflows/deploy-self-hosted.yml)): tras un CI exitoso (o manualmente), el runner autoalojado (Windows) levanta los contenedores localmente y luego ejecuta [`scripts/deploy.sh`](scripts/deploy.sh) vía Git Bash para entregar la aplicación al equipo par por SSH.

`scripts/deploy.sh` transfiere `docker-compose.yml`, los Dockerfiles y el código fuente al equipo destino con `scp` (no `rsync`, para no depender de que esté instalado en Windows), y allí ejecuta `docker compose up -d --build`. Requiere `ssh`/`scp` en el PATH (cliente OpenSSH de Windows o el que trae Git Bash) y estas variables de entorno (configuradas como secrets del repositorio: `PEER_HOST`, `PEER_SSH_USER`, `PEER_DEPLOY_DIR`, `PEER_SSH_KEY_PATH` opcional):

```bash
REMOTE_HOST=<ip-equipo-par> REMOTE_USER=<usuario-ssh> REMOTE_DIR=<ruta-destino> bash scripts/deploy.sh
```

`scripts/configure-firewall.ps1` configura el Firewall de Windows en el PC Ops para exponer únicamente SSH, Backend y Frontend (ejecutar en PowerShell como Administrador):

```powershell
.\scripts\configure-firewall.ps1
```

---

## Solución de problemas

- **`javac`/`java` no reconocido:** instala JDK 17+ y agrégalo al PATH.
- **Puerto ocupado:** pasa un puerto distinto como argumento a los scripts, o ajusta los mapeos en `docker-compose.yml`.
- **Error de conexión al calcular:** verifica la IP/puerto del Backend y las reglas de firewall del puerto correspondiente.
- **No se guarda el historial:** verifica permisos de escritura en `data/` (modo local) o que el volumen `history-data` exista (modo Docker).
- **Falla `scripts/deploy.sh`:** confirma conectividad SSH (`ssh <usuario>@<ip>`) y que `REMOTE_HOST`, `REMOTE_USER` y `REMOTE_DIR` estén definidos.
