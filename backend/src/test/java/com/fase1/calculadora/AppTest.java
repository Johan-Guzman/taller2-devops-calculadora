package com.fase1.calculadora;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
public final class AppTest {
    public static void main(String[] args) throws Exception {
        testCalculatorService();
        testHistoryRepository();
        testSeparatedTopology();
        System.out.println("OK - Todas las pruebas pasaron");
    }
    private static void testCalculatorService() {
        CalculatorService service = new CalculatorService();
        assertEquals("5", service.sum(new BigDecimal("2"), new BigDecimal("3")).toPlainString(), "suma");
        assertEquals("-1", service.subtract(new BigDecimal("2"), new BigDecimal("3")).toPlainString(), "resta");
        assertEquals("6", service.multiply(new BigDecimal("2"), new BigDecimal("3")).toPlainString(), "multiplicación");
        assertEquals("4", service.divide(new BigDecimal("8"), new BigDecimal("2")).toPlainString(), "división");
        try {
            service.divide(BigDecimal.ONE, BigDecimal.ZERO);
            throw new IllegalStateException("La división entre cero debía fallar");
        } catch (IllegalArgumentException exception) {
            assertEquals("No se puede dividir entre cero", exception.getMessage(), "mensaje división entre cero");
        }
    }
    private static void testHistoryRepository() throws Exception {
        Path tempFile = Files.createTempDirectory("fase1-history-test").resolve("history.jsonl");
        HistoryRepository repository = new HistoryRepository(tempFile);
        for (int i = 1; i <= 7; i++) {
            repository.append("{\"id\":" + i + "}");
        }
        List<String> last = repository.last(5);
        assertEquals("5", Integer.toString(last.size()), "cantidad historial");
        assertEquals("{\"id\":7}", last.get(0), "historial más reciente");
        assertEquals("{\"id\":3}", last.get(4), "quinto elemento del historial");
        assertEquals("true", Boolean.toString(repository.isWritable()), "persistencia escribible");
    }
    private static void testSeparatedTopology() throws Exception {
        Path tempRoot = Files.createTempDirectory("fase1-separated-test");
        Path historyFile = tempRoot.resolve("data/history.jsonl");
        Path frontendDirectory = Path.of("frontend");
        int backendPort = freePort();
        int frontendPort = freePort();
        String backendUrl = "http://127.0.0.1:" + backendPort;
        CalculatorServer backendApplication = new CalculatorServer(new HistoryRepository(historyFile));
        FrontendServer frontendApplication = new FrontendServer(frontendDirectory, backendUrl);
        HttpServer backendServer = backendApplication.createServer(backendPort);
        HttpServer frontendServer = frontendApplication.createServer(frontendPort);
        backendServer.start();
        frontendServer.start();
        HttpClient client = HttpClient.newHttpClient();
        try {
            HttpResponse<String> frontendResponse = get(client, "http://127.0.0.1:" + frontendPort + "/");
            assertEquals("200", Integer.toString(frontendResponse.statusCode()), "status Frontend separado");
            if (!frontendResponse.body().contains("Calculadora distribuida")) {
                throw new IllegalStateException("Falló contenido del Frontend separado");
            }
            if (!frontendResponse.body().contains("<option value=\"divide\">Dividir</option>")) {
                throw new IllegalStateException("El Frontend no contiene la operación de división");
            }
            int configScriptIndex = frontendResponse.body().indexOf("<script src=\"/config.js\"></script>");
            int appScriptIndex = frontendResponse.body().indexOf("<script src=\"app.js\"></script>");
            if (configScriptIndex < 0) {
                throw new IllegalStateException("index.html no carga /config.js");
            }
            if (appScriptIndex < 0) {
                throw new IllegalStateException("index.html no carga app.js");
            }
            if (configScriptIndex > appScriptIndex) {
                throw new IllegalStateException("index.html debe cargar /config.js antes de app.js");
            }
            HttpResponse<String> configResponse = get(client, "http://127.0.0.1:" + frontendPort + "/config.js");
            assertEquals("200", Integer.toString(configResponse.statusCode()), "status config Frontend");
            if (!configResponse.body().contains(backendUrl)) {
                throw new IllegalStateException("El Frontend no recibió la URL del Backend");
            }
            FrontendServer autoConfigApplication = new FrontendServer(frontendDirectory, backendUrl, "auto:9099");
            int autoConfigPort = freePort();
            HttpServer autoConfigServer = autoConfigApplication.createServer(autoConfigPort);
            autoConfigServer.start();
            try {
                HttpResponse<String> autoConfigResponse = get(client, "http://127.0.0.1:" + autoConfigPort + "/config.js");
                assertContains(autoConfigResponse.body(), "http://127.0.0.1:9099", "URL pública automática para Docker");
            } finally {
                autoConfigServer.stop(0);
            }
            String appJavaScript = Files.readString(frontendDirectory.resolve("app.js"), StandardCharsets.UTF_8);
            if (!appJavaScript.contains("window.APP_CONFIG?.backendUrl")
                    || !appJavaScript.contains("${backendUrl}/api/${operation}")
                    || !appJavaScript.contains("${backendUrl}/api/history")) {
                throw new IllegalStateException("El Frontend no usa la URL del Backend entregada por /config.js");
            }
            if (!appJavaScript.contains("divide: '/'")) {
                throw new IllegalStateException("El Frontend no representa la división en el historial");
            }
            assertCorsPreflight(client, backendUrl + "/api/sum");
            assertCorsPreflight(client, backendUrl + "/api/divide");
            assertHttpResult(client, backendUrl, "/api/sum", "{\"a\":2,\"b\":3}", "\"result\":5");
            assertHttpResult(client, backendUrl, "/api/subtract", "{\"a\":10,\"b\":4}", "\"result\":6");
            assertHttpResult(client, backendUrl, "/api/multiply", "{\"a\":2.5,\"b\":4}", "\"result\":10");
            assertHttpResult(client, backendUrl, "/api/divide", "{\"a\":8,\"b\":2}", "\"result\":4");
            assertHttpError(
                    client,
                    backendUrl,
                    "/api/divide",
                    "{\"a\":1,\"b\":0}",
                    HttpURLConnection.HTTP_BAD_REQUEST,
                    "No se puede dividir entre cero"
            );
            assertHttpResult(client, backendUrl, "/api/sum", "{\"a\":1,\"b\":1}", "\"result\":2");
            assertHttpResult(client, backendUrl, "/api/subtract", "{\"a\":8,\"b\":3}", "\"result\":5");
            assertHttpResult(client, backendUrl, "/api/multiply", "{\"a\":3,\"b\":3}", "\"result\":9");
            HttpResponse<String> healthResponse = get(client, backendUrl + "/health");
            assertEquals("200", Integer.toString(healthResponse.statusCode()), "status health Backend");
            assertContains(healthResponse.body(), "\"status\":\"UP\"", "estado health Backend");
            assertContains(healthResponse.body(), "\"uptimeSeconds\":", "uptime health Backend");
            assertContains(healthResponse.body(), "\"persistenceWritable\":true", "persistencia health Backend");
            HttpResponse<String> statusResponse = get(client, "http://127.0.0.1:" + frontendPort + "/status");
            assertEquals("200", Integer.toString(statusResponse.statusCode()), "status endpoint Frontend");
            assertContains(statusResponse.body(), "\"status\":\"UP\"", "estado Frontend");
            assertContains(statusResponse.body(), "\"uptimeSeconds\":", "uptime Frontend");
            assertContains(statusResponse.body(), "\"backendStatus\":\"UP\"", "estado Backend desde Frontend");
            assertContains(statusResponse.body(), "\"persistenceWritable\":true", "persistencia desde Frontend");
            HttpResponse<String> historyResponse = get(client, backendUrl + "/api/history");
            assertEquals("200", Integer.toString(historyResponse.statusCode()), "status historial separado");
            assertEquals("5", Integer.toString(countOccurrences(historyResponse.body(), "\"timestamp\"")), "ultimas 5 operaciones");
            if (!historyResponse.body().contains("\"result\":9") || historyResponse.body().contains("\"operation\":\"sum\",\"a\":2,\"b\":3,\"result\":5")) {
                throw new IllegalStateException("Falló el contenido del historial separado");
            }
            if (!historyResponse.body().contains("\"operation\":\"divide\"")) {
                throw new IllegalStateException("La división exitosa no quedó registrada en el historial");
            }
        } finally {
            frontendServer.stop(0);
            backendServer.stop(0);
        }
        assertEquals("7", Integer.toString(Files.readAllLines(historyFile, StandardCharsets.UTF_8).size()), "persistencia de 7 operaciones");
        int restartedBackendPort = freePort();
        String restartedBackendUrl = "http://127.0.0.1:" + restartedBackendPort;
        CalculatorServer restartedApplication = new CalculatorServer(new HistoryRepository(historyFile));
        HttpServer restartedServer = restartedApplication.createServer(restartedBackendPort);
        restartedServer.start();
        try {
            HttpResponse<String> historyAfterRestart = get(client, restartedBackendUrl + "/api/history");
            assertEquals("200", Integer.toString(historyAfterRestart.statusCode()), "status historial tras reinicio");
            assertEquals("5", Integer.toString(countOccurrences(historyAfterRestart.body(), "\"timestamp\"")), "historial persistente tras reinicio");
            HttpResponse<String> healthAfterRestart = get(client, restartedBackendUrl + "/health");
            assertEquals("200", Integer.toString(healthAfterRestart.statusCode()), "health tras reinicio");
            assertContains(healthAfterRestart.body(), "\"persistenceWritable\":true", "persistencia tras reinicio");
        } finally {
            restartedServer.stop(0);
        }
    }
    private static void assertHttpResult(HttpClient client, String baseUrl, String endpoint, String json, String expectedFragment) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(Integer.toString(HttpURLConnection.HTTP_OK), Integer.toString(response.statusCode()), "status " + endpoint);
        if (!response.body().contains(expectedFragment)) {
            throw new IllegalStateException("Falló " + endpoint + ": " + response.body());
        }
    }
    private static void assertHttpError(HttpClient client, String baseUrl, String endpoint, String json, int expectedStatus, String expectedFragment) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(Integer.toString(expectedStatus), Integer.toString(response.statusCode()), "status error " + endpoint);
        assertContains(response.body(), expectedFragment, "mensaje error " + endpoint);
    }
    private static void assertCorsPreflight(HttpClient client, String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Origin", "http://127.0.0.1:8081")
                .header("Access-Control-Request-Method", "POST")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals("204", Integer.toString(response.statusCode()), "preflight CORS");
        assertEquals("*", response.headers().firstValue("Access-Control-Allow-Origin").orElse(""), "CORS origin");
    }
    private static HttpResponse<String> get(HttpClient client, String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
    private static int countOccurrences(String value, String fragment) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(fragment, index)) >= 0) {
            count++;
            index += fragment.length();
        }
        return count;
    }
    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
    private static void assertContains(String value, String fragment, String name) {
        if (!value.contains(fragment)) {
            throw new IllegalStateException("Falló " + name + ". No se encontró: " + fragment + " en: " + value);
        }
    }
    private static void assertEquals(String expected, String actual, String name) {
        if (!expected.equals(actual)) {
            throw new IllegalStateException("Falló " + name + ". Esperado: " + expected + ", obtenido: " + actual);
        }
    }
}
