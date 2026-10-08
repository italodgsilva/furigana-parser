package com.shinji2324.furigana.reading;

import com.shinji2324.furigana.japanese.Token;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Decorador de {@link ReadingProvider} que evita consultas repetidas.
 *
 * <p>Fluxo para cada token:
 * <ol>
 *   <li>se a superfície já está no {@link FileReadingCache}, devolve a leitura em cache
 *       sem consultar o provider delegado;</li>
 *   <li>caso contrário, consulta o delegado e, em caso de sucesso, grava o resultado
 *       no cache;</li>
 *   <li>termos não encontrados ficam registrados apenas em memória durante a execução
 *       atual — falhas (incluindo indisponibilidade do serviço) não são persistidas,
 *       para que possam ser tentadas novamente em execuções futuras.</li>
 * </ol>
 *
 * <p>O cache é consultado pela superfície do token, que é a chave estável entre
 * execuções e independente da posição na legenda.
 */
public class CachingReadingProvider implements ReadingProvider {

    private final ReadingProvider delegate;
    private final FileReadingCache cache;
    private final Set<String> misses = new HashSet<>();

    /**
     * Cria o decorador.
     *
     * @param delegate provider consultado em caso de ausência no cache
     * @param cache    cache em disco/in-memória
     */
    public CachingReadingProvider(ReadingProvider delegate, FileReadingCache cache) {
        this.delegate = delegate;
        this.cache = cache;
    }

    @Override
    public Optional<Reading> findReading(Token token) {
        if (token == null || token.getSurface() == null || token.getSurface().isBlank()) {
            return Optional.empty();
        }

        String key = token.getSurface();

        Reading cached = cache.get(key);
        if (cached != null) {
            return Optional.of(cached);
        }

        if (misses.contains(key)) {
            return Optional.empty();
        }

        Optional<Reading> found = delegate.findReading(token);
        found.ifPresent(reading -> cache.put(key, reading));
        if (found.isEmpty()) {
            misses.add(key);
        }
        return found;
    }
}
