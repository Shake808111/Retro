package dev.netzwerk.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

class ApiApplicationTest {

    @Test
    void healthAntwortetOk() throws Exception {
        HttpServer server = ApiApplication.start(0);
        try (HttpClient client = HttpClient.newBuilder().proxy(HttpClient.Builder.NO_PROXY).build()) {
            URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/health");
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertEquals("ok", response.body());
        } finally {
            server.stop(0);
        }
    }
}
