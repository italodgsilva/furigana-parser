# Furigana Parser

[![CI](https://github.com/shinji2324/furigana-parser/actions/workflows/ci.yml/badge.svg)](https://github.com/shinji2324/furigana-parser/actions/workflows/ci.yml)

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

Próximas etapas (fora do escopo atual):

- integração dinâmica com o MPV (scripts Lua, atualização em tempo real).

## Tecnologias

- Java 21
- Maven
- JUnit 5
- Jackson
- Kuromoji (tokenização morfológica, dicionário IPADIC)
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
│   │   │       └── mpv/
│   │   │
│   │   └── resources/
│   │       └── ass/
│   │           └── default-style.ass
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
    └── mpv.md
```

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

### Linha de comando

O formato de saída é escolhido pela extensão do arquivo: `.json` gera o modelo
intermediário e `.ass` gera a legenda com o furigana posicionado.

```
java -jar target/furigana-parser-0.1.0-SNAPSHOT.jar entrada.srt saida.json
java -jar target/furigana-parser-0.1.0-SNAPSHOT.jar entrada.srt saida.ass
java -jar target/furigana-parser-0.1.0-SNAPSHOT.jar --mpv entrada.srt video.mkv
java -jar target/furigana-parser-0.1.0-SNAPSHOT.jar --help
```

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
para não quebrar a sincronização da legenda.

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
Dialogue: 0,0:00:01.00,0:00:04.50,Furigana,,0,0,0,,{\an5\pos(717,928)}きょう
Dialogue: 0,0:00:01.00,0:00:04.50,Furigana,,0,0,0,,{\an5\pos(872,928)}にほんご
Dialogue: 0,0:00:01.00,0:00:04.50,Furigana,,0,0,0,,{\an5\pos(1026,928)}べんきょう
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
