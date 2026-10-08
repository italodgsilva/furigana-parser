# Arquitetura

## Visão geral

O Furigana Parser será construído como um pipeline de processamento de texto.

A arquitetura inicial é:

```text
┌─────────────┐
│     SRT     │
└──────┬──────┘
       │
       ▼
┌─────────────────┐
│ Subtitle Parser │
└────────┬────────┘
         │
         ▼
┌────────────────────┐
│ Japanese Tokenizer │
└──────────┬─────────┘
           │
           ▼
┌────────────────────┐
│  Reading Provider  │
└──────────┬─────────┘
           │
           ├──────────────┐
           ▼              ▼
      ┌─────────┐    ┌─────────┐
      │  Jisho  │    │  Cache  │
      └─────────┘    └─────────┘
           │
           ▼
┌────────────────────┐
│  FuriganaDocument  │
└──────────┬─────────┘
           │
           ├───────────────┐
           ▼               ▼
      ┌─────────┐      ┌─────────┐
      │  JSON   │      │   ASS   │
      └─────────┘      └────┬────┘
                             │
                             ▼
                           MPV
````

 ## Camadas

 ### `subtitle`

 Responsável pela leitura e representação das legendas.

 Exemplos:

```
Subtitle
SubtitleEntry
SubtitleParser
SrtParser
```

 Essa camada não deve possuir conhecimento sobre japonês, furigana ou Jisho.

 ### `japanese`

 Responsável pelo processamento específico do idioma japonês.

 A principal responsabilidade inicial será a tokenização.

 Exemplo:

```
今日は日本語を勉強します。
```

 pode ser segmentado como:

```
今日 | は | 日本語 | を | 勉強 | します | 。
```

 A implementação concreta do tokenizador passou a ser o Kuromoji, com o
 dicionário IPADIC: além de segmentar, ele identifica a classe gramatical e
 fornece a leitura de cada token (normalizada para hiragana). O
 `ScriptJapaneseTokenizer` (segmentação por tipo de escrita) permanece como
 alternativa leve/offline, sem leituras. Cada `Token` carrega superfície, classe
 gramatical, leitura e deslocamentos no texto tokenizado.

 ### `reading`

 Responsável por obter as leituras dos tokens.

 A abstração principal será:

```
interface ReadingProvider {
    Optional<Reading> findReading(Token token);
}
```

 A leitura tem prioridade vinda do próprio tokenizador (Kuromoji). Quando ele
 não a conhece, e apenas para tokens que contêm kanji, consulta-se o Jisho como
 fallback:

```
JishoReadingProvider
LocalDictionaryProvider (futuro)
```

 A aplicação não deve depender diretamente de uma implementação específica: o
 provider escolhido é decorado por um `CachingReadingProvider`.

 ### `reading.cache`

 Responsável por armazenar resultados de consultas.

 Fluxo conceitual:

```
Token
  │
  ▼
Cache
  │
  ├── encontrado → retorna resultado
  │
  └── não encontrado
           │
           ▼
      ReadingProvider
           │
           ▼
         Cache
```

 O cache deverá evitar consultas repetidas para o mesmo termo.

 Implementação atual: `CachingReadingProvider` (decorador) + `FileReadingCache`,
 com um arquivo JSON que mapeia superfície → leitura (padrão:
 `data/cache/readings.json`). O cache é best-effort — falhas de leitura/escrita
 não interrompem o processamento — e não persiste termos não encontrados.

 ### `furigana`

 Contém o modelo intermediário utilizado pelo restante da aplicação.

 A camada deve representar a relação entre texto original e leitura sem assumir como o resultado será renderizado.

 Isso é importante porque furigana pode posteriormente ser convertido para diferentes formatos.

 Também concentra a segmentação de um token em kanji (com leitura) e okurigana
 (sem leitura), via `FuriganaSegmenter`, alinhando a superfície à leitura em
 hiragana.

 ### `output`

 Responsável pela geração dos formatos de saída.

 Inicialmente:

```
JsonWriter
AssWriter
```

 Novos formatos poderão ser adicionados sem alterar as camadas anteriores.

 ### `mpv`

 A integração com o MPV será implementada posteriormente.

 Ela poderá incluir:

 - scripts Lua;
- atalhos;
- carregamento automático de legendas;
- comunicação com a aplicação;
- atualização dinâmica da legenda.

 Essa camada não faz parte da primeira versão.

 ## Fluxo de dados

 O fluxo principal deverá ser:

```
SubtitleEntry
      │
      ▼
String
      │
      ▼
JapaneseTokenizer
      │
      ▼
List<Token>
      │
      ▼
ReadingProvider
      │
      ▼
List<Token + Reading>
      │
      ▼
FuriganaDocument
      │
      ├──→ JsonWriter
      │
      └──→ AssWriter
```

 ## Modelo conceitual

 ### Token

 Representa uma unidade identificada durante a tokenização.

 Exemplo:

```
surface = "日本語"
```

 ### Reading

 Representa uma leitura associada a um termo.

 Exemplo:

```
surface = "日本語"
reading = "にほんご"
```

 ### FuriganaSegment

 Representa uma unidade que poderá ser renderizada.

 Exemplo conceitual:

```
baseText = "日本語"
reading = "にほんご"
```

 Para elementos que não possuem furigana:

```
baseText = "を"
reading = null
```

 ## Independência entre componentes

 As seguintes dependências devem ser evitadas:

```
SrtParser → Jisho
SrtParser → ASS
Jisho → SRT
Jisho → MPV
AssWriter → Jisho
```

 Em vez disso:

```
SRT → Subtitle
Subtitle → Tokenizer
Tokenizer → Token
Token → ReadingProvider
Token + Reading → FuriganaDocument
FuriganaDocument → Output
```

 ## Decisões ainda abertas

 As seguintes decisões serão tomadas durante a implementação:

 - tratamento de palavras com múltiplas leituras;
- tratamento de nomes próprios;
- estratégia de posicionamento do furigana em ASS;
- integração final com MPV.

 Decisões já tomadas:

 - tokenização: Kuromoji (IPADIC);
- leituras: prioridade para o tokenizador, com fallback no Jisho;
- cache: JSON em `data/cache/readings.json`, via `CachingReadingProvider`;
- okurigana: separado dentro do token pelo `FuriganaSegmenter`;
- verbos conjugados: tokenizados pelo Kuromoji (radical + okurigana).

 Essas decisões não devem ser antecipadas sem necessidade.

 ## Princípio de evolução

 Cada etapa deve produzir uma saída verificável.

 Por exemplo:

```
SRT
 ↓
Subtitle
 ↓
tokens
 ↓
tokens + readings
 ↓
JSON
 ↓
ASS
```

 Isso permite testar cada estágio independentemente e identificar erros sem depender do MPV ou da renderização final.
