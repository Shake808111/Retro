package net.retromc.core.data.nick;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/** {@link HttpTransport} über den {@link HttpClient} des JDK (Zeitlimit 10 s, normale Weiterleitungen). */
public final class JdkHttpTransport implements HttpTransport {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    @Override
    public Response get(URI uri, Map<String, String> headers) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET();
        headers.forEach(request::header);
        HttpResponse<String> response = client.send(request.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        Optional<Duration> retryAfter = response.headers().firstValue("Retry-After")
                .flatMap(JdkHttpTransport::seconds);
        return new Response(response.statusCode(), response.body() == null ? "" : response.body(), retryAfter);
    }

    /** {@code Retry-After} in Sekunden (die Datumsform nutzt Mojang nicht). */
    static Optional<Duration> seconds(String value) {
        try {
            long seconds = Long.parseLong(value.strip());
            return seconds >= 0 ? Optional.of(Duration.ofSeconds(seconds)) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
