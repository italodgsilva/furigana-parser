package com.shinji2324.furigana.font;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Minimal abstraction of an HTTP request that returns bytes.
 *
 * <p>Isolates {@link FontDownloader} from {@code java.net.http}, allowing the
 * download/caching behavior to be tested without accessing the network.
 */
@FunctionalInterface
public interface ByteFetcher {

    /**
     * Performs a GET and returns the response body.
     *
     * @param url absolute URL to query
     * @return response bytes, or {@link Optional#empty()} on failure
     */
    Optional<byte[]> fetch(String url);

    /**
     * Creates the default fetcher, based on {@code java.net.http.HttpClient}.
     *
     * <p>Network failures, timeouts, and responses other than {@code 200} result
     * in {@link Optional#empty()} — the downloader tolerates unavailable services.
     *
     * @return real HTTP fetcher
     */
    static ByteFetcher defaultFetcher() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        return url -> {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofMinutes(2))
                        .header("User-Agent", "furigana-parser")
                        .GET()
                        .build();

                HttpResponse<byte[]> response =
                        client.send(request, HttpResponse.BodyHandlers.ofByteArray());

                if (response.statusCode() == 200 && response.body().length > 0) {
                    return Optional.of(response.body());
                }
                return Optional.empty();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            } catch (IOException | IllegalArgumentException e) {
                return Optional.empty();
            }
        };
    }
}