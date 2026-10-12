# Furigana Parser

[![CI](https://github.com/shinji2324/furigana-parser/actions/workflows/ci.yml/badge.svg)](https://github.com/shinji2324/furigana-parser/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/shinji2324/furigana-parser)](https://github.com/shinji2324/furigana-parser/releases)

Ferramenta para processamento de legendas japonesas, com o objetivo de adicionar leituras em furigana aos kanji e gerar legendas compatíveis com players como o MPV.

O projeto está sendo desenvolvido de forma incremental, mantendo o processamento das legendas separado da renderização e da integração com o player.

## Objetivos

O objetivo principal é transformar uma legenda japonesa comum em uma representação estruturada contendo as leituras dos termos japoneses.

Fluxo:

```
SRT
 │
 ▼
Subtitle Parser
 │
 ▼
Japanese Tokenizer
 │
 ▼
Reading Provider
 │
 ├── Kuromoji (principal)
 │
 └── Jisho (fallback)
 │
 ▼
FuriganaDocument
 │
 ├── JSON
 │
 └── ASS
 │
 ▼
MPV
```

## Estado atual

O projeto está em desenvolvimento incremental, com saída verificável ao final de cada etapa.

Etapas concluídas:

1. Parser de SRT (fail-fast em blocos malformados, índices opcionais, timestamps preservados como string crua).
2. Modelo interno do furigana — `FuriganaDocument` → `FuriganaEntry` (id, start, end, texto, segmentos) → `FuriganaSegment` (texto + leitura).
3. Tokenização de texto japonês — interface `JapaneseTokenizer`, com a implementação provisória `ScriptJapaneseTokenizer` (por tipo de escrita).
4. Pipeline SRT → JSON intermediário, orquestrado por `FuriganaPipeline` e executável pela CLI (`Application`).
5. Tokenização morfológica com Kuromoji (dicionário IPADIC), com leitura em hiragana e separação de kanji/okurigana (`FuriganaSegmenter`).
6. Leituras externas: `JishoReadingProvider` real, mediado por `CachingReadingProvider` com cache em disco (`data/cache/readings.json`).
7. Geração de ASS com furigana posicionado (`AssGenerator` + `AssWriter` + `AssLayout`), com estilos em `src/main/resources/ass/default-style.ass`.
8. Integração com o MPV (`--mpv`): gera o ASS ao lado da SRT e abre o vídeo com `mpv --sub-file=...` (ver `docs/mpv.md`).
9. Embutição da fonte no próprio ASS (`--embed-font`): localiza a fonte do estilo pelo nome de família, recorta a face de coleções `.ttc` e escreve a seção `[Fonts]`, tudo em Java. Com `--subset-font`, reduz a fonte embutida aos glifos usados na legenda, por meio do HarfBuzz (ver `docs/font-embedding.md`).
10. Limpeza de texto (`--strip-parentheses`): remove os trechos entre parênteses redondos (full-width e half-width) antes da tokenização, descartando entradas que ficam vazias.
11. Build nativo com o GraalVM (`mvn -Pnative package`): gera um executável sem JVM, com as configurações de reflexão/recursos/JNI em `src/main/resources/META-INF/native-image/` (ver `docs/native-image.md`).
12. Publicação automática de versões: uma tag `v*` dispara o workflow de release, que gera o JAR e os binários nativos de cada plataforma e os anexa a um GitHub Release (ver a seção "Publicar uma versão").

Próximas etapas (fora do escopo atual):

- integração dinâmica com o MPV (scripts Lua, atualização em tempo real).

## Tecnologias

- Java 21
- Maven
- JUnit 5
- Jackson
- Kuromoji (tokenização morfológica, dicionário IPADIC)
- JNA (acesso à biblioteca nativa do HarfBuzz, para o subsetting opcional da fonte)
- GraalVM Native Image (build nativo opcional)
- Formato SRT
- Formato ASS/SSA
- MPV
- Jisho, como fonte de fallback para leituras

## Estrutura do projeto

```text
furigana-parser/
│
├── pom.xml
├── README.md
├── LICENSE
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/shinji2324/furigana/
│   │   │       ├── Application.java
│   │   │       ├── cli/
│   │   │       ├── subtitle/
│   │   │       ├── japanese/
│   │   │       ├── reading/
│   │   │       ├── furigana/
│   │   │       ├── pipeline/
│   │   │       ├── output/
│   │   │       ├── font/
│   │   │       └── mpv/
│   │   │
│   │   └── resources/
│   │       ├── ass/
│   │       │   └── default-style.ass
│   │       └── META-INF/
│   │           └── native-image/
│   │               └── com.shinji2324/furigana-parser/
│   │                   ├── reflect-config.json
│   │                   ├── resource-config.json
│   │                   ├── jni-config.json
│   │                   ├── proxy-config.json
│   │                   └── native-image.properties
│   │
│   └── test/
│       ├── java/
│       └── resources/
│
├── data/
│   ├── cache/
│   └── output/
│
└── docs/
    ├── architecture.md
    ├── ass-format.md
    ├── font-embedding.md
    ├── mpv.md
    └── native-image.md
```

## Download e uso

Cada versão publicada deixa o JAR e os binários prontos na página de
[Releases](https://github.com/shinji2324/furigana-parser/releases). Baixe o
arquivo da sua plataforma:

| Plataforma | Arquivo |
| --- | --- |
| Linux x86-64 | `furigana-parser-<versão>-linux-x86_64` |
| Linux arm64 | `furigana-parser-<versão>-linux-aarch64` |
| macOS Apple Silicon | `furigana-parser-<versão>-macos-aarch64` |
| macOS Intel | `furigana-parser-<versão>-macos-x86_64` |
| Windows x86-64 | `furigana-parser-<versão>-windows-x86_64.exe` |
| Qualquer sistema (requer Java 21+) | `furigana-parser-<versão>.jar` |

Os binários nativos não precisam de nada instalado além do próprio arquivo; o
JAR precisa de um Java 21 ou superior.

No Linux e no macOS, marque o binário como executável antes de rodar:

```
chmod +x furigana-parser-0.1.0-linux-x86_64
./furigana-parser-0.1.0-linux-x86_64 entrada.srt saida.ass
```

No Windows, basta executar o `.exe` (na primeira vez, o SmartScreen pode pedir
confirmação). No macOS, se o Gatekeeper bloquear o binário não assinado, libere-o
com `xattr -d com.apple.quarantine <arquivo>` ou em Ajustes → Privacidade e
Segurança.

Cada Release inclui um `SHA256SUMS.txt` para conferir a integridade dos
downloads:

```
sha256sum -c SHA256SUMS.txt
```

A lista completa de opções está em [Linha de comando](#linha-de-comando).

## Desenvolvimento

Requisitos:

- JDK 21 ou superior
- Maven 3.9 ou superior

Compilar:

```
mvn clean compile
```

Executar os testes:

```
mvn test
```

Gerar o JAR (com as dependências embutidas, ex.: Jackson e Kuromoji):

```
mvn package
```

Gerar o executável nativo com o GraalVM (opcional; requer GraalVM e um toolchain
C — veja [`docs/native-image.md`](docs/native-image.md)):

```
mvn -Pnative package
```

A saída é `target/furigana-parser`.

### Publicar uma versão

As versões são publicadas automaticamente pelo workflow
[`release.yml`](.github/workflows/release.yml): basta criar e enviar uma tag
`v*`.

```
git tag v0.1.0
git push origin v0.1.0
```

O workflow compila o JAR (executando os testes) e os binários nativos de Linux
(x86-64 e aarch64), macOS (x86-64 e arm64) e Windows, e anexa tudo a um GitHub
Release com o nome da tag, além de um `SHA256SUMS.txt`. Também funciona criando a
Release pela interface do GitHub: a tag criada dispara o workflow, que anexa os
arquivos à Release existente.

O número da versão nos nomes dos arquivos vem da tag (sem o `v`), de modo que
`v0.1.0` gera `furigana-parser-0.1.0.jar`,
`furigana-parser-0.1.0-linux-x86_64`, `furigana-parser-0.1.0-windows-x86_64.exe`
etc.

### Linha de comando

O formato de saída é escolhido pela extensão do arquivo: `.json` gera o modelo
intermediário e `.ass` gera a legenda com o furigana posicionado.

Nos exemplos abaixo, troque `java -jar target/furigana-parser-0.1.0-SNAPSHOT.jar`
pelo caminho do JAR baixado (ex.: `java -jar furigana-parser-0.1.0.jar`) ou pelo
binário nativo (ex.: `./furigana-parser-0.1.0-linux-x86_64`).

```
java -jar target/furigana-parser-0.1.0-SNAPSHOT.jar entrada.srt saida.json
java -jar target/furigana-parser-0.1.0-SNAPSHOT.jar entrada.srt saida.ass
java -jar target/furigana-parser-0.1.0-SNAPSHOT.jar --embed-font entrada.srt saida.ass
java -jar target/furigana-parser-0.1.0-SNAPSHOT.jar --embed-font=fonte.otf entrada.srt saida.ass
java -jar target/furigana-parser-0.1.0-SNAPSHOT.jar --embed-font --subset-font entrada.srt saida.ass
java -jar target/furigana-parser-0.1.0-SNAPSHOT.jar --strip-parentheses entrada.srt saida.ass
java -jar target/furigana-parser-0.1.0-SNAPSHOT.jar --mpv entrada.srt video.mkv
java -jar target/furigana-parser-0.1.0-SNAPSHOT.jar --help
```

Os mesmos comandos valem para o binário nativo: troque
`java -jar target/furigana-parser-0.1.0-SNAPSHOT.jar` por `./target/furigana-parser`.

Com `--embed-font`, o ASS passa a carregar a própria fonte na seção `[Fonts]`,
dispensando instalar a fonte na máquina de quem assiste. A opção funciona também
no modo `--mpv`. Sem argumento, a fonte é localizada pelo nome de família do
estilo (`Noto Sans CJK JP`); com `--embed-font=<arquivo>`, o arquivo informado é
usado. Como o arquivo inteiro é embutido, o ASS cresce bastante (cerca de 22 MB
com a `Noto Sans CJK JP`).

Com `--subset-font` (que exige `--embed-font`), apenas os glifos usados na
legenda são gravados, o que reduz drasticamente o arquivo (um episódio inteiro
fica em torno de 300 KB). A redução usa o HarfBuzz (`libharfbuzz-subset`); se a
biblioteca não estiver disponível, o programa avisa (com a sugestão de instalação
para o seu sistema) e embute a fonte completa.
Veja [`docs/font-embedding.md`](docs/font-embedding.md).

Com `--strip-parentheses`, os trechos entre parênteses redondos — em largura total
(`（…）`) ou meia-largura (`(…)`) — são removidos do texto antes da tokenização.
Isso evita gerar furigana (e consultar leituras) para nomes de personagens,
reações e indicações de cena (ex.: `（笑）`, `(Taro)`). Se uma entrada ficar
inteiramente vazia, como uma linha que era só `（笑）`, ela é descartada. A opção
vale para JSON, ASS e `--mpv`.

No modo `--mpv`, o ASS é gerado ao lado da SRT (`entrada.furigana.ass`) e o vídeo
é aberto no MPV com `--sub-file`. Requer o MPV instalado no `PATH`. As limitações
dessa integração (resolução fixa, fonte CJK, faixas de legenda etc.) estão em
[`docs/mpv.md`](docs/mpv.md).

O JSON gerado tem a seguinte estrutura (cada token é separado em kanji, com
leitura, e okurigana, sem leitura):

```json
{
  "entries": [
    {
      "id": "1",
      "start": "00:00:01,000",
      "end": "00:00:04,500",
      "text": "今日は日本語を勉強します。",
      "segments": [
        { "text": "今日", "reading": "きょう" },
        { "text": "は", "reading": null },
        { "text": "日本語", "reading": "にほんご" },
        { "text": "を", "reading": null },
        { "text": "勉強", "reading": "べんきょう" },
        { "text": "し", "reading": null },
        { "text": "ます", "reading": null },
        { "text": "。", "reading": null }
      ]
    }
  ]
}
```

`id`, `start` e `end` preservam a sincronização original da SRT. As leituras vêm
do Kuromoji e, quando ele não conhece um termo com kanji, do Jisho (mediado por
cache em `data/cache/readings.json`). Entradas sem kanji não são descartadas,
para não quebrar a sincronização da legenda. Quando nenhuma fonte conhece um
termo com kanji, ele fica sem furigana e um aviso é emitido em `stderr`.

O ASS gerado (estratégia `\pos` descrita em `docs/ass-format.md`) usa dois
estilos — `Main` para o texto e `Furigana` para as leituras — e posiciona cada
leitura sobre o kanji correspondente:

```text
[Script Info]
...
[V4+ Styles]
Style: Main,...
Style: Furigana,...
[Events]
Dialogue: 0,0:00:01.00,0:00:04.50,Main,,0,0,0,,{\an5\pos(960,988)}今日は日本語を勉強します。
Dialogue: 0,0:00:01.00,0:00:04.50,Furigana,,0,0,0,,{\an5\pos(717,951)}きょう
Dialogue: 0,0:00:01.00,0:00:04.50,Furigana,,0,0,0,,{\an5\pos(872,951)}にほんご
Dialogue: 0,0:00:01.00,0:00:04.50,Furigana,,0,0,0,,{\an5\pos(1026,951)}べんきょう
```

## Princípios do projeto

### Separação de responsabilidades

O parser de legendas não deve conhecer o Jisho.

O `ReadingProvider` não deve conhecer o formato SRT.

O gerador de ASS não deve depender diretamente do Jisho.

Cada etapa deve trabalhar com modelos internos bem definidos.

### Formato intermediário

O projeto utiliza uma representação intermediária entre a obtenção das leituras e a geração da saída.

Isso permite gerar diferentes formatos a partir dos mesmos dados:

```
FuriganaDocument
    │
    ├── JSON
    ├── ASS
    └── futuramente outros formatos
```

### Cache

Consultas externas devem ser armazenadas em cache sempre que possível.

Isso reduz:

- número de requisições;
- tempo de processamento;
- dependência de disponibilidade do serviço;
- possibilidade de repetir consultas desnecessariamente.

O cache de leituras é gravado em `data/cache/readings.json` (um objeto JSON de
superfície → leitura). Termos não encontrados não são persistidos, para poderem
ser consultados novamente em execuções futuras. O processamento funciona offline:
se o Jisho estiver indisponível, as leituras ausentes ficam `null`.

## Exemplo conceitual

Uma legenda como:

```
今日は日本語を勉強します。
```

é representada internamente de forma semelhante a:

```
今日       きょう
は
日本語     にほんご
を
勉強       べんきょう
し
ます
。
```

## Licença

Este projeto é distribuído sob a [MIT License](LICENSE).
