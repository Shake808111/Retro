package net.retromc.core.data.nick;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** HTTP-Attrappe ohne Netz: Antworten je Adresse, aufgezeichnete Aufrufe und Kopfzeilen. */
final class FakeHttp implements HttpTransport {

    /** Antwort, die einen Netzfehler auslöst. */
    static final Response IO_ERROR = Response.of(-1, "");

    final List<URI> calls = new ArrayList<>();
    final List<Map<String, String>> headers = new ArrayList<>();
    private final Function<URI, Response> handler;

    FakeHttp(Function<URI, Response> handler) {
        this.handler = handler;
    }

    @Override
    public Response get(URI uri, Map<String, String> requestHeaders) throws IOException {
        calls.add(uri);
        headers.add(Map.copyOf(requestHeaders));
        Response response = handler.apply(uri);
        if (response == IO_ERROR) {
            throw new IOException("kein Netz");
        }
        return response;
    }

    long count(String part) {
        return calls.stream().filter(uri -> uri.toString().contains(part)).count();
    }

    /** Sessionserver-Antwort mit signierter Textur. */
    static String profile(String id, String value, String signature) {
        return "{\"id\":\"" + id + "\",\"name\":\"Quelle\",\"properties\":[{\"name\":\"textures\",\"value\":\""
                + value + "\",\"signature\":\"" + signature + "\"}]}";
    }

    static Response tooMany(long seconds) {
        return new Response(429, "", java.util.Optional.of(Duration.ofSeconds(seconds)));
    }
}
