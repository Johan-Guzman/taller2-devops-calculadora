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

            String appJavaScript = Files.readString(frontendDirectory.resolve("app.js"), StandardCharsets.UTF_8);
            if (!appJavaScript.contains("${backendUrl}/api/${operation}") || !appJavaScript.contains("${backendUrl}/api/history")) {
                throw new IllegalStateException("El Frontend no dirige las peticiones REST al Backend configurado");
            }

            assertCorsPreflight(client, backendUrl + "/api/sum");
            assertHttpResult(client, backendUrl, "/api/sum", "{\"a\":2,\"b\":3}", "\"result\":5");
            assertHttpResult(client, backendUrl, "/api/subtract", "{\"a\":10,\"b\":4}", "\"result\":6");
            assertHttpResult(client, backendUrl, "/api/multiply", "{\"a\":2.5,\"b\":4}", "\"result\":10");
            assertHttpResult(client, backendUrl, "/api/sum", "{\"a\":1,\"b\":1}", "\"result\":2");
            assertHttpResult(client, backendUrl, "/api/subtract", "{\"a\":8,\"b\":3}", "\"result\":5");
            assertHttpResult(client, backendUrl, "/api/multiply", "{\"a\":3,\"b\":3}", "\"result\":9");

            HttpResponse<String> historyResponse = get(client, backendUrl + "/api/history");
            assertEquals("200", Integer.toString(historyResponse.statusCode()), "status historial separado");
            assertEquals("5", Integer.toString(countOccurrences(historyResponse.body(), "\"timestamp\"")), "ultimas 5 operaciones");
            if (!historyResponse.body().contains("\"result\":9") || historyResponse.body().contains("\"operation\":\"sum\",\"a\":2,\"b\":3,\"result\":5")) {
                throw new IllegalStateException("Falló el contenido del historial separado");
            }
        } finally {
            frontendServer.stop(0);
            backendServer.stop(0);
        }

        assertEquals("6", Integer.toString(Files.readAllLines(historyFile, StandardCharsets.UTF_8).size()), "persistencia de 6 operaciones");

        int restartedBackendPort = freePort();
        String restartedBackendUrl = "http://127.0.0.1:" + restartedBackendPort;
        CalculatorServer restartedApplication = new CalculatorServer(new HistoryRepository(historyFile));
        HttpServer restartedServer = restartedApplication.createServer(restartedBackendPort);
        restartedServer.start();
        try {
            HttpResponse<String> historyAfterRestart = get(client, restartedBackendUrl + "/api/history");
            assertEquals("200", Integer.toString(historyAfterRestart.statusCode()), "status historial tras reinicio");
            assertEquals("5", Integer.toString(countOccurrences(historyAfterRestart.body(), "\"timestamp\"")), "historial persistente tras reinicio");
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

    private static void assertEquals(String expected, String actual, String name) {
        if (!expected.equals(actual)) {
            throw new IllegalStateException("Falló " + name + ". Esperado: " + expected + ", obtenido: " + actual);
        }
    }
}
