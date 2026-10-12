package com.shinji2324.furigana.reading.jisho;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * Minimal abstraction of an HTTP request that returns text.
 *
 * <p>Isolates {@link JishoReadingProvider} from {@code java.net.http}, allowing
 * the parsing of responses to be tested without accessing the network.
 */
@FunctionalInterface
public interface HttpTextFetcher {

    /**
     * Performs a GET and returns the response body.
     *
     * @param url absolute URL to query
     * @return response body, or {@link Optional#empty()} on failure
     */
    Optional<String> fetch(String url);

    /**
     * Creates the default fetcher, based on {@code java.net.http.HttpClient}.
     *
     * <p>Network failures, timeouts, and responses other than {@code 200} result
     * in {@link Optional#empty()} — the provider must be tolerant of unavailable
     * services.
     *
     * @return real HTTP fetcher
     */
    static HttpTextFetcher defaultFetcher() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        return url -> {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(20))
                        .header("Accept", "application/json")
                        .header("User-Agent", "furigana-parser")
                        .GET()
                        .build();

                HttpResponse<String> response =
                        client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                if (response.statusCode() == 200) {
                    return Optional.of(response.body());
                }
                return Optional.empty();
            } catch (IOException e) {
                return Optional.empty();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        };
    }
}
