package com.mall.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class MallServiceApp {
    private static final int PORT = 8080;
    private static final Map<String, StoreInfo> STORES = new LinkedHashMap<>();
    private static final AtomicInteger TICKET_COUNTER = new AtomicInteger(100);

    static {
        STORES.put("TechHub Electronics", new StoreInfo("Level 2, North Wing", "Near escalators by Food Court", "9:00 AM - 9:00 PM"));
        STORES.put("Urban Threads", new StoreInfo("Level 1, Central Atrium", "Across from main fountain", "10:00 AM - 9:00 PM"));
        STORES.put("GreenLeaf Cafe", new StoreInfo("Level 1, East Promenade", "Outdoor patio entrance", "8:00 AM - 8:00 PM"));
        STORES.put("KidsPlay Books", new StoreInfo("Level 2, South Wing", "Next to Family Restrooms", "10:00 AM - 8:30 PM"));
        STORES.put("Apex Fitness Gear", new StoreInfo("Level 3, West Wing", "Beside cinema lobby", "10:00 AM - 9:00 PM"));
    }

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);

        server.createContext("/health", exchange -> {
            String body = "{\"status\":\"UP\",\"service\":\"MallBackgroundService\",\"port\":" + PORT + "}";
            sendJson(exchange, 200, body);
        });

        server.createContext("/stores", exchange -> {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            String query = getQueryParam(exchange.getRequestURI().getRawQuery(), "q");
            String filter = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);

            StringBuilder json = new StringBuilder("[");
            boolean first = true;
            for (Map.Entry<String, StoreInfo> entry : STORES.entrySet()) {
                String name = entry.getKey();
                StoreInfo info = entry.getValue();
                boolean matches = filter.isEmpty()
                        || name.toLowerCase(Locale.ROOT).contains(filter)
                        || info.location().toLowerCase(Locale.ROOT).contains(filter);
                if (matches) {
                    if (!first) json.append(",");
                    first = false;
                    json.append("{")
                        .append("\"name\":\"").append(escape(name)).append("\",")
                        .append("\"location\":\"").append(escape(info.location())).append("\",")
                        .append("\"directions\":\"").append(escape(info.directions())).append("\",")
                        .append("\"hours\":\"").append(escape(info.hours())).append("\"")
                        .append("}");
                }
            }
            json.append("]");
            sendJson(exchange, 200, json.toString());
        });

        server.createContext("/services/request", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"Use POST to log a service request\"}");
                return;
            }
            String rawBody;
            try (InputStream is = exchange.getRequestBody()) {
                rawBody = new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
            String requestedType = rawBody.isEmpty() ? "General Concierge Assistance" : rawBody;
            int ticketId = TICKET_COUNTER.incrementAndGet();
            String loggedAt = LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a"));

            String json = "{"
                    + "\"ticket\":\"SVC-" + ticketId + "\","
                    + "\"service\":\"" + escape(requestedType) + "\","
                    + "\"status\":\"DISPATCHED\","
                    + "\"loggedAt\":\"" + loggedAt + "\""
                    + "}";
            sendJson(exchange, 201, json);
        });

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("MallBackgroundService listening on http://127.0.0.1:" + PORT);
    }

    private static String getQueryParam(String rawQuery, String key) {
        if (rawQuery == null || rawQuery.isBlank()) return null;
        for (String pair : rawQuery.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2 && parts[0].equals(key)) {
                return URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record StoreInfo(String location, String directions, String hours) {}
}