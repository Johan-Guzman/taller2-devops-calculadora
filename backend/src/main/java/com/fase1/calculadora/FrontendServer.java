package com.fase1.calculadora;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
public final class FrontendServer {
    private final Path frontendDirectory;
    private final String backendUrl;
    private final String publicBackendUrl;
    private final long startedAtNanos;
    private final HttpClient httpClient;
    public FrontendServer(Path frontendDirectory, String backendUrl) {
        this(frontendDirectory, backendUrl, backendUrl);
    }
    public FrontendServer(Path frontendDirectory, String backendUrl, String publicBackendUrl) {
        this.frontendDirectory = frontendDirectory.toAbsolutePath().normalize();
        this.backendUrl = normalizeBackendUrl(backendUrl);
        this.publicBackendUrl = normalizePublicBackendUrl(publicBackendUrl);
        this.startedAtNanos = System.nanoTime();
        this.httpClient = HttpClient.newHttpClient();
    }
    public static void main(String[] args) throws Exception {
        if (args.length == 0 || args[0].isBlank()) {
            throw new IllegalArgumentException("Debe indicar la URL del Backend, por ejemplo http://192.168.1.10:8082");
        }
        String backendUrl = args[0];
        int port = args.length > 1 ? parsePort(args[1], "Frontend") : 8081;
        String publicBackendUrl = args.length > 2 ? args[2] : backendUrl;
        FrontendServer application = new FrontendServer(Path.of("frontend"), backendUrl, publicBackendUrl);
        HttpServer server = application.createServer(port);
        server.start();
        System.out.println("Frontend disponible en http://0.0.0.0:" + port);
        System.out.println("Backend interno: " + application.backendUrl);
        System.out.println("Backend para navegador: " + application.publicBackendUrl);
    }
    public HttpServer createServer(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/config.js", this::handleConfig);
        server.createContext("/status", this::handleStatus);
        server.createContext("/api", this::handleApiProxy);
        server.createContext("/", new StaticHandler(frontendDirectory));
        return server;
    }
    private void handleConfig(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "Método no permitido", "text/plain; charset=utf-8");
            return;
        }
        String browserBackendUrl = resolvePublicBackendUrl(exchange);
        String body = "window.APP_CONFIG = Object.freeze({backendUrl:\""
                + escapeJavaScript(browserBackendUrl) + "\"});";
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        send(exchange, 200, body, "application/javascript; charset=utf-8");
    }
    private void handleStatus(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "{\"error\":\"Método no permitido\"}", "application/json; charset=utf-8");
            return;
        }
        long uptimeSeconds = uptimeSeconds();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(backendUrl + "/health"))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            boolean backendUp = response.statusCode() == 200 && response.body().contains("\"status\":\"UP\"");
            boolean persistenceWritable = response.body().contains("\"persistenceWritable\":true");
            String status = backendUp && persistenceWritable ? "UP" : "DEGRADED";
            send(
                    exchange,
                    200,
                    "{\"status\":\"" + status + "\",\"uptimeSeconds\":" + uptimeSeconds
                            + ",\"backendStatus\":\"" + (backendUp ? "UP" : "DEGRADED") + "\""
                            + ",\"persistenceWritable\":" + persistenceWritable + "}",
                    "application/json; charset=utf-8"
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            sendBackendDown(exchange, uptimeSeconds);
        } catch (Exception exception) {
            sendBackendDown(exchange, uptimeSeconds);
        }
    }
    // Proxy auxiliar para diagnóstico; la interfaz web usa la URL publicada en /config.js.
    private void handleApiProxy(HttpExchange exchange) throws IOException {
        try {
            String targetUrl = backendUrl + exchange.getRequestURI();
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(URI.create(targetUrl));
            String method = exchange.getRequestMethod();
            if ("POST".equalsIgnoreCase(method)) {
                byte[] body = exchange.getRequestBody().readAllBytes();
                String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
                requestBuilder
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                        .header("Content-Type", contentType != null ? contentType : "application/json");
            } else {
                requestBuilder.method(method, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<byte[]> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofByteArray());
            exchange.getResponseHeaders().set(
                    "Content-Type",
                    response.headers().firstValue("Content-Type").orElse("application/json; charset=utf-8")
            );
            exchange.sendResponseHeaders(response.statusCode(), response.body().length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(response.body());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            send(exchange, 503, "{\"error\":\"Backend no disponible\"}", "application/json; charset=utf-8");
        } catch (Exception exception) {
            send(exchange, 502, "{\"error\":\"No fue posible comunicarse con el Backend\"}", "application/json; charset=utf-8");
        }
    }
    private String resolvePublicBackendUrl(HttpExchange exchange) {
        if (!publicBackendUrl.startsWith("auto:")) {
            return publicBackendUrl;
        }
        String port = publicBackendUrl.substring("auto:".length());
        String hostHeader = exchange.getRequestHeaders().getFirst("Host");
        String host = hostHeader == null || hostHeader.isBlank() ? "localhost" : extractHost(hostHeader);
        return "http://" + host + ":" + port;
    }
    private static String extractHost(String hostHeader) {
        if (hostHeader.startsWith("[")) {
            int closingBracket = hostHeader.indexOf(']');
            return closingBracket >= 0 ? hostHeader.substring(0, closingBracket + 1) : hostHeader;
        }
        int firstColon = hostHeader.indexOf(':');
        int lastColon = hostHeader.lastIndexOf(':');
        return firstColon >= 0 && firstColon == lastColon ? hostHeader.substring(0, firstColon) : hostHeader;
    }
    private static String normalizeBackendUrl(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("La URL del Backend no puede estar vacía");
        }
        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (!(normalized.startsWith("http://") || normalized.startsWith("https://"))) {
            throw new IllegalArgumentException("La URL del Backend debe comenzar por http:// o https://");
        }
        return normalized;
    }
    private static String normalizePublicBackendUrl(String value) {
        if (value != null && value.startsWith("auto:")) {
            String port = value.substring("auto:".length());
            parsePort(port, "Backend público");
            return "auto:" + port;
        }
        return normalizeBackendUrl(value);
    }
    private static int parsePort(String value, String service) {
        try {
            int port = Integer.parseInt(value);
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("El puerto del " + service + " debe estar entre 1 y 65535");
            }
            return port;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("El puerto del " + service + " debe ser numérico", exception);
        }
    }
    private long uptimeSeconds() {
        return (System.nanoTime() - startedAtNanos) / 1_000_000_000L;
    }
    private static void sendBackendDown(HttpExchange exchange, long uptimeSeconds) throws IOException {
        send(
                exchange,
                503,
                "{\"status\":\"DOWN\",\"uptimeSeconds\":" + uptimeSeconds
                        + ",\"backendStatus\":\"DOWN\",\"persistenceWritable\":false}",
                "application/json; charset=utf-8"
        );
    }
    private static String escapeJavaScript(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
    private static void send(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }
    private record StaticHandler(Path frontendDirectory) implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                send(exchange, 405, "Método no permitido", "text/plain; charset=utf-8");
                return;
            }
            String requestPath = exchange.getRequestURI().getPath();
            String relative = "/".equals(requestPath) ? "index.html" : requestPath.substring(1);
            Path file = frontendDirectory.resolve(relative).normalize();
            if (!file.startsWith(frontendDirectory) || !Files.exists(file) || Files.isDirectory(file)) {
                send(exchange, 404, "No encontrado", "text/plain; charset=utf-8");
                return;
            }
            byte[] content = Files.readAllBytes(file);
            exchange.getResponseHeaders().set("Content-Type", contentType(file));
            exchange.sendResponseHeaders(200, content.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(content);
            }
        }
        private static String contentType(Path file) {
            String name = file.getFileName().toString().toLowerCase();
            if (name.endsWith(".html")) {
                return "text/html; charset=utf-8";
            }
            if (name.endsWith(".js")) {
                return "application/javascript; charset=utf-8";
            }
            if (name.endsWith(".css")) {
                return "text/css; charset=utf-8";
            }
            return "application/octet-stream";
        }
    }
}
