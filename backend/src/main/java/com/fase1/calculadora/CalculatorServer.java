package com.fase1.calculadora;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CalculatorServer {
    private static final Pattern NUMBER_A = Pattern.compile("\\\"a\\\"\\s*:\\s*([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?)");
    private static final Pattern NUMBER_B = Pattern.compile("\\\"b\\\"\\s*:\\s*([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?)");

    private final CalculatorService calculatorService;
    private final HistoryRepository historyRepository;

    public CalculatorServer(HistoryRepository historyRepository) {
        this.calculatorService = new CalculatorService();
        this.historyRepository = historyRepository;
    }

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        CalculatorServer application = new CalculatorServer(
                new HistoryRepository(Path.of("data", "history.jsonl"))
        );
        HttpServer server = application.createServer(port);
        server.start();
        System.out.println("Backend disponible en http://0.0.0.0:" + port);
    }

    public HttpServer createServer(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/sum", exchange -> handleCalculation(exchange, "sum"));
        server.createContext("/api/subtract", exchange -> handleCalculation(exchange, "subtract"));
        server.createContext("/api/multiply", exchange -> handleCalculation(exchange, "multiply"));
        server.createContext("/api/history", this::handleHistory);
        return server;
    }

    private void handleCalculation(HttpExchange exchange, String operation) throws IOException {
        addCors(exchange.getResponseHeaders());
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 204, "", "application/json; charset=utf-8");
            return;
        }
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"Método no permitido\"}");
            return;
        }

        try {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            BigDecimal a = extractNumber(body, NUMBER_A, "a");
            BigDecimal b = extractNumber(body, NUMBER_B, "b");
            BigDecimal result = switch (operation) {
                case "sum" -> calculatorService.sum(a, b);
                case "subtract" -> calculatorService.subtract(a, b);
                case "multiply" -> calculatorService.multiply(a, b);
                default -> throw new IllegalStateException("Operación no soportada");
            };

            String record = calculationJson(operation, a, b, result, Instant.now().toString());
            historyRepository.append(record);
            sendJson(exchange, 200, record);
        } catch (IllegalArgumentException exception) {
            sendJson(exchange, 400, "{\"error\":\"" + escapeJson(exception.getMessage()) + "\"}");
        } catch (Exception exception) {
            sendJson(exchange, 500, "{\"error\":\"Error interno del servidor\"}");
        }
    }

    private void handleHistory(HttpExchange exchange) throws IOException {
        addCors(exchange.getResponseHeaders());
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 204, "", "application/json; charset=utf-8");
            return;
        }
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"Método no permitido\"}");
            return;
        }

        try {
            List<String> history = historyRepository.last(5);
            sendJson(exchange, 200, "[" + String.join(",", history) + "]");
        } catch (Exception exception) {
            sendJson(exchange, 500, "{\"error\":\"No fue posible consultar el historial\"}");
        }
    }

    private static BigDecimal extractNumber(String body, Pattern pattern, String field) {
        Matcher matcher = pattern.matcher(body);
        if (!matcher.find()) {
            throw new IllegalArgumentException("El campo '" + field + "' es obligatorio y debe ser numérico");
        }
        try {
            return new BigDecimal(matcher.group(1));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("El campo '" + field + "' debe ser numérico");
        }
    }

    private static String calculationJson(String operation, BigDecimal a, BigDecimal b, BigDecimal result, String timestamp) {
        return "{"
                + "\"timestamp\":\"" + escapeJson(timestamp) + "\","
                + "\"operation\":\"" + escapeJson(operation) + "\","
                + "\"a\":" + normalized(a) + ","
                + "\"b\":" + normalized(b) + ","
                + "\"result\":" + normalized(result)
                + "}";
    }

    private static String normalized(BigDecimal number) {
        BigDecimal normalized = number.stripTrailingZeros();
        if (normalized.scale() < 0) {
            normalized = normalized.setScale(0);
        }
        return normalized.toPlainString();
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void addCors(Headers headers) {
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type");
    }

    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        send(exchange, status, body, "application/json; charset=utf-8");
    }

    private static void send(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        if (status == 204) {
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
            return;
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }
}
