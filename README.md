# Furigana Parser

[![CI](https://github.com/shinji2324/furigana-parser/actions/workflows/ci.yml/badge.svg)](https://github.com/shinji2324/furigana-parser/actions/workflows/ci.yml)

Ferramenta para processamento de legendas japonesas, com o objetivo de adicionar leituras em furigana aos kanji e posteriormente gerar legendas compatíveis com players como o MPV.

O projeto está sendo desenvolvido de forma incremental, mantendo o processamento das legendas separado da renderização e da integração com o player.

## Objetivos

O objetivo principal é transformar uma legenda japonesa comum em uma representação estruturada contendo as leituras dos termos japoneses.

Fluxo planejado:

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
 ├── Jisho
 │
 └── futuramente outras fontes
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
4. Pipeline SRT → JSON intermediário, orquestrado por `FuriganaPipeline` e executável pela CLI (`Application`). Leituras são opcionais: sem provider, ficam `null`.

Próximas etapas:

- Tokenização morfológica com Kuromoji (kanji + okurigana dentro de um mesmo token).
- Integração do Jisho como `ReadingProvider` real, com cache em disco.
- Geração de ASS com furigana posicionado (estratégia `\pos` do docs/ass-format.md).
- Integração com MPV.

## Tecnologias

- Java 21
- Maven
- JUnit 5
- Jackson
- Formato SRT
- Formato ASS/SSA
- MPV
- Jisho, inicialmente como fonte de dados para leituras

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
│   │   │       ├── config/
│   │   │       ├── subtitle/
│   │   │       ├── japanese/
│   │   │       ├── reading/
│   │   │       ├── furigana/
│   │   │       ├── pipeline/
│   │   │       ├── output/
│   │   │       └── mpv/
│   │   │
│   │   └── resources/
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
    └── ass-format.md
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

 Gerar o JAR (com as dependências embutidas, ex.: Jackson):

```
mvn package
```

 ### Linha de comando

 Processar uma legenda SRT e gerar o JSON intermediário:

```
java -jar target/furigana-parser-0.1.0-SNAPSHOT.jar entrada.srt saida.json
```

 O JSON gerado tem a seguinte estrutura (um segmento por token da entrada):

```json
{
  "entries": [
    {
      "id": "1",
      "start": "00:00:01,000",
      "end": "00:00:04,500",
      "text": "今日は日本語を勉強します。",
      "segments": [
        { "text": "今日", "reading": null },
        { "text": "は", "reading": null }
      ]
    }
  ]
}
```

 `id`, `start` e `end` preservam a sincronização original da SRT. `reading` fica
 `null` enquanto os providers de leitura (Kuromoji/Jisho — etapa posterior) não
 forem integrados. Entradas sem kanji não são descartadas, para não quebrar a
 sincronização da legenda.

 ## Princípios do projeto

 ### Separação de responsabilidades

 O parser de legendas não deve conhecer o Jisho.

 O `ReadingProvider` não deve conhecer o formato SRT.

 O gerador de ASS não deve depender diretamente do Jisho.

 Cada etapa deve trabalhar com modelos internos bem definidos.

 ### Formato intermediário

 O projeto utilizará uma representação intermediária entre a obtenção das leituras e a geração da saída.

 Isso permitirá gerar diferentes formatos a partir dos mesmos dados:

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

 ## Exemplo conceitual

 Uma legenda como:

```
今日は日本語を勉強します。
```

 deve eventualmente ser representada internamente de forma semelhante a:

```
今日       きょう
は
日本語     にほんご
を
勉強       べんきょう
します
。
```

 A forma exata do modelo ainda poderá mudar durante o desenvolvimento.

 ## Licença

Este projeto é distribuído sob a [MIT License](LICENSE).
