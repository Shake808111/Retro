package dev.netzwerk.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.netzwerk.core.api.BuildInfo;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Platzhalter der Web-API: bietet nur {@code GET /health}. Das Framework (Ktor oder Spring Boot,
 * Notiz 07, Abschnitt 7) wird beim Start der Umsetzung gewählt.
 */
public final class ApiApplication {

    private static final Logger LOG = System.getLogger(ApiApplication.class.getName());

    private ApiApplication() {
    }

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("NETWORK_API_PORT", "8080"));
        HttpServer server = start(port);
        LOG.log(Level.INFO, "{0}-API (Platzhalter) lauscht auf Port {1}.", BuildInfo.BRAND_NAME, server.getAddress().getPort());
    }

    /** Startet den Server; Port 0 wählt einen freien Port (für Tests). */
    public static HttpServer start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health", ApiApplication::health);
        server.start();
        return server;
    }

    private static void health(HttpExchange exchange) throws IOException {
        byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
