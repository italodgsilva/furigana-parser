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
 * Abstração mínima de uma requisição HTTP que devolve texto.
 *
 * <p>Isola o {@link JishoReadingProvider} do {@code java.net.http}, permitindo
 * testar o parsing das respostas sem acessar a rede.
 */
@FunctionalInterface
public interface HttpTextFetcher {

    /**
     * Executa um GET e devolve o corpo da resposta.
     *
     * @param url URL absoluta a consultar
     * @return corpo da resposta, ou {@link Optional#empty()} em caso de falha
     */
    Optional<String> fetch(String url);

    /**
     * Cria o fetcher padrão, baseado em {@code java.net.http.HttpClient}.
     *
     * <p>Falhas de rede, timeout e respostas diferentes de {@code 200} resultam em
     * {@link Optional#empty()} — o provider deve ser tolerante a indisponibilidade.
     *
     * @return fetcher HTTP real
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
