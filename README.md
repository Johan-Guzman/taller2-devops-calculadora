# Calculadora Distribuida Java - Fase 1

**Versión:** 1.0.2  
**Arquitectura:** Cliente-Servidor (Frontend y Backend separados)

Aplicación web distribuida donde el Frontend realiza peticiones HTTP/REST dinámicas hacia el Backend. Toda la lógica de negocio (suma, resta, multiplicación) y la persistencia del historial de operaciones se gestionan en el servidor.

---

## Requisitos Previos

* **Java Development Kit (JDK):** Versión 17 o superior.
* Los comandos `java` y `javac` deben ser accesibles desde la terminal (configurados en el PATH).

---

## Ejecución de Pruebas (Tests)

El proyecto incluye un conjunto de pruebas automatizadas que levantan ambos servicios, validan el HTML servido, configuración de CORS, operaciones matemáticas y persistencia.

Ejecutar desde la raíz del proyecto:
* **Windows:** `test.bat`
* **Linux/macOS:** `./test.sh`

**Resultado esperado:** `OK - Todas las pruebas pasaron`

---

## Guía de Despliegue (Dos Equipos)

La misma carpeta del proyecto se puede usar tanto en el equipo servidor como en el cliente. Debes ejecutar los comandos desde la carpeta raíz.

### 1. Desplegar el Backend
Elige un puerto (por defecto `8080`).

* **Windows:** `run-backend.bat 8080`
* **Linux/macOS:** `./run-backend.sh 8080`

> **Importante:** Obtén la dirección IPv4 del equipo Backend (`ipconfig` en Windows, `hostname -I` en Linux). Asegúrate de que el firewall permita conexiones entrantes en el puerto elegido.

### 2. Desplegar el Frontend
Inicia el Frontend pasándole la URL exacta del Backend y el puerto local para el Frontend (por defecto `8081`).

* **Windows:** `run-frontend.bat http://<IP_BACKEND>:8080 8081`
* **Linux/macOS:** `./run-frontend.sh http://<IP_BACKEND>:8080 8081`

*Nota: No uses `localhost` ni `127.0.0.1` en la URL del Backend si los servicios están en equipos diferentes.*

Accede a la aplicación abriendo en el navegador del equipo Frontend: `http://localhost:8081`

---

## Casos de Uso (Funcionamiento Esperado)

* **HU1 - Servicio de Suma:** Ingresa dos números, selecciona "Sumar" y calcula. La petición va al endpoint `/api/sum`.
* **HU2 - Multi-Operación:** Permite alternar entre Suma, Resta y Multiplicación con resultados dinámicos procesados por el backend.
* **HU3 - Historial (Persistencia):** Muestra las últimas 5 operaciones exitosas. Sobrevive a reinicios del Backend.

---

## Endpoints del Backend

| Método | Endpoint | Descripción | Ejemplo de Cuerpo (JSON) |
|---|---|---|---|
| **POST** | `/api/sum` | Realiza una suma | `{"a":2,"b":3}` |
| **POST** | `/api/subtract` | Realiza una resta | `{"a":10,"b":4}` |
| **POST** | `/api/multiply` | Realiza una multiplicación | `{"a":2.5,"b":4}` |
| **GET** | `/api/history` | Retorna las últimas 5 operaciones | *N/A* |

**Ejemplo de Respuesta:**
`{"timestamp":"2026-08-10T20:00:00Z","operation":"sum","a":2,"b":3,"result":5}`

---

## Persistencia de Datos

Cada cálculo exitoso se guarda automáticamente como una línea JSON en el servidor en la ruta:
`data/history.jsonl`

Esta carpeta y archivo se generan en la primera operación. **No elimines este archivo** si deseas conservar el historial entre reinicios del servidor.

---

## Solución de Problemas (Troubleshooting)

* **`javac` no reconocido:** Instala JDK 17+ y asegúrate de agregarlo a las variables de entorno.
* **Puerto ocupado:** Si el Backend o Frontend fallan al iniciar, pasa un puerto diferente como argumento en los scripts.
* **Error de conexión al calcular:** Verifica que la IP del Backend en el script del Frontend sea la correcta y no sea `localhost` (si estás en equipos separados). Comprueba las reglas del firewall.
* **No se guarda el historial:** Verifica que el usuario que ejecuta el Backend tenga permisos de escritura en la carpeta raíz para crear el directorio `data/`.

---

## Historial de Versiones (Changelog)

* **v1.0.2 (Actual):** Corrección del ticket HU1. Se modificó `index.html` para garantizar la carga de `/config.js` antes de `app.js`, asegurando que `window.APP_CONFIG` exista antes de inicializar la app. Pruebas actualizadas para validar esta corrección.
* **v1.0.1:** Versión anterior reportada por Operations.