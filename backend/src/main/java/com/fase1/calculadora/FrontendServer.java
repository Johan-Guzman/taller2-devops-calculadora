package com.fase1.calculadora;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FrontendServer {
    private final Path frontendDirectory;
    private final String backendUrl;

    public FrontendServer(Path frontendDirectory, String backendUrl) {
        this.frontendDirectory = frontendDirectory.toAbsolutePath().normalize();
        this.backendUrl = normalizeBackendUrl(backendUrl);
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || args[0].isBlank()) {
            throw new IllegalArgumentException("Debe indicar la URL del Backend, por ejemplo http://192.168.1.10:8080");
        }
        String backendUrl = args[0];
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 8081;
        FrontendServer application = new FrontendServer(Path.of("frontend"), backendUrl);
        HttpServer server = application.createServer(port);
        server.start();
        System.out.println("Frontend disponible en http://localhost:" + port);
        System.out.println("Backend configurado: " + application.backendUrl);
    }

    public HttpServer createServer(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/config.js", this::handleConfig);
        server.createContext("/", new StaticHandler(frontendDirectory));
        return server;
    }

    private void handleConfig(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "Método no permitido", "text/plain; charset=utf-8");
            return;
        }
        String body = "window.APP_CONFIG = Object.freeze({backendUrl:\"" + escapeJavaScript(backendUrl) + "\"});";
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        send(exchange, 200, body, "application/javascript; charset=utf-8");
    }

    private static String normalizeBackendUrl(String value) {
        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (!(normalized.startsWith("http://") || normalized.startsWith("https://"))) {
            throw new IllegalArgumentException("La URL del Backend debe comenzar por http:// o https://");
        }
        return normalized;
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

    private static final class StaticHandler implements HttpHandler {
        private final Path frontendDirectory;

        private StaticHandler(Path frontendDirectory) {
            this.frontendDirectory = frontendDirectory;
        }

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
