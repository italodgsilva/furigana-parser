# Executável nativo (GraalVM)

## Objetivo

Gerar um binário autônomo a partir do projeto, sem depender da JVM em tempo de
execução. O binário não exige um Java instalado na máquina de destino, inicia
mais rápido e ocupa menos memória do que a execução via `java -jar`.

## Requisitos

- **GraalVM** (Community ou Oracle) com o `native-image`, como `JAVA_HOME` (ou no
  `PATH`). O projeto usa Java 21, então um GraalVM para JDK 21 é o par natural.
- **Toolchain C**: um compilador `gcc` e os cabeçalhos/biblioteca da zlib.
  - Debian/Ubuntu: `sudo apt install build-essential zlib1g-dev`
  - Fedora: `sudo dnf install gcc zlib-devel`
  - macOS: `xcode-select --install`

## Uso

```
mvn -Pnative package
```

A saída é `target/furigana-parser`, um executável comum. A linha de comando é a
mesma da versão em JAR:

```
./target/furigana-parser entrada.srt saida.json
./target/furigana-parser entrada.srt saida.ass
./target/furigana-parser --embed-font --subset-font entrada.srt saida.ass
./target/furigana-parser --help
```

O profile `native` faz duas coisas:

1. **Desativa o `maven-shade-plugin`.** O uber jar seria entregue ao
   `native-image` *além* dos jars de dependência, duplicando classes e, o que
   pesa de verdade, os recursos — o dicionário do Kuromoji (~33 MB) entraria duas
   vezes, inflando o binário em cerca de 35 MB. No build nativo, o
   `native-image` lê `target/classes` + as dependências. Como consequência, esse
   profile gera apenas o jar "fino" (não o uber jar).
2. **Executa o `native-maven-plugin`** (`native:compile-no-fork`) na fase
   `package`.

## Configuração do native-image

As configurações ficam em
`src/main/resources/META-INF/native-image/com.shinji2324/furigana-parser/` e são
descobertas automaticamente (tanto pelo Maven quanto por um `native-image -jar`
manual, já que também ficam dentro do jar):

| Arquivo | Para quê |
| --- | --- |
| `reflect-config.json` | Reflexão: o modelo `FuriganaDocument` / `FuriganaEntry` / `FuriganaSegment` (serializado pelo Jackson), classes do JNA e do provedor TLS/HTTP do Jisho. |
| `resource-config.json` | Recursos lidos em runtime: os `.bin` do dicionário Kuromoji, `ass/default-style.ass` e a `libjnidispatch` do JNA. |
| `jni-config.json` | Acesso JNI usado pelo JNA. |
| `proxy-config.json` | Proxy dinâmico da interface JNA `HarfBuzzSubsetLibrary$HarfBuzz`. |
| `native-image.properties` | Argumentos fixos, hoje `--no-fallback`. |

Por que cada um é necessário:

- o **Kuromoji** carrega o dicionário IPADIC como recurso do classpath;
- o **Jackson** serializa os records por reflexão (`JsonWriter`);
- o **JNA** carrega a `libharfbuzz-subset` dinamicamente, extrai a
  `libjnidispatch` do próprio jar em runtime e usa JNI.

O padrão do recurso `jnidispatch` em `resource-config.json` cobre todas as
plataformas (`com/sun/jna/.*jnidispatch\.(so|dylib|jnilib|dll)`), para que o
mesmo arquivo sirva em Linux, macOS e Windows.

### Regenerar as configurações

Se o modelo ou as dependências mudarem (nova serialização reflexiva, novo recurso
lido do classpath), regenere com o *tracing agent* do native-image:

```
GRAALVM=/caminho/do/graalvm
CFG=src/main/resources/META-INF/native-image/com.shinji2324/furigana-parser

# 1) rota JSON (Jackson) — config-output-dir sobrescreve
"$GRAALVM/bin/java" -agentlib:native-image-agent=config-output-dir=$CFG \
  -jar target/furigana-parser-0.1.0-SNAPSHOT.jar entrada.srt saida.json

# 2) rota ASS + subset (JNA) — config-merge-dir acumula
"$GRAALVM/bin/java" -agentlib:native-image-agent=config-merge-dir=$CFG \
  -jar target/furigana-parser-0.1.0-SNAPSHOT.jar \
  --embed-font --subset-font entrada.srt saida.ass
```

Use `config-output-dir` na primeira execução e `config-merge-dir` nas seguintes
(ou diretórios separados, mesclando depois). Ao terminar, confira se o padrão
multiplataforma do `jnidispatch` continua em `resource-config.json`, pois o agent
grava o caminho específico da plataforma em que rodou.

## Resultados medidos

No ambiente de desenvolvimento (Linux x86-64, GraalVM CE 21.0.2, entrada de duas
legendas):

| | Binário nativo | `java -jar` |
| --- | --- | --- |
| Inicialização | ~0,75 s | ~1,7 s |
| Memória (RSS) | ~112 MB | ~255 MB |
| Artefato | ~85 MB | ~18 MB (jar) |

As saídas — JSON, ASS e ASS com `--embed-font --subset-font` — são **idênticas,
byte a byte**, às da JVM.

## Limites

- O binário depende, em runtime, de bibliotecas de sistema comuns (`libz`,
  `libc`, `libpthread`).
- O subsetting (`--subset-font`) continua exigindo a `libharfbuzz-subset`
  instalada: o binário nativo não a embute. A ausência é tratada como na versão
  JVM (aviso e embutição da fonte completa). Veja
  [`font-embedding.md`](font-embedding.md).
- O build nativo não faz parte da CI de todo push (exigiria GraalVM e um
  toolchain C em cada execução). Ele é gerado apenas no workflow de release,
  em runners próprios que já trazem esses componentes.
