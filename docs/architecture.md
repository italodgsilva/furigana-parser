# Architecture

## Overview

The Furigana Parser will be built as a text-processing pipeline.

The initial architecture is:

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

## Layers

### `subtitle`

Responsible for reading and representing the subtitles.

Examples:

```
Subtitle
SubtitleEntry
SubtitleParser
SrtParser
```

This layer must not have any knowledge of Japanese, furigana or Jisho.

### `japanese`

Responsible for processing specific to the Japanese language.

The main initial responsibility will be tokenization.

Example:

```
今日は日本語を勉強します。
```

can be segmented as:

```
今日 | は | 日本語 | を | 勉強 | します | 。
```

The concrete implementation of the tokenizer has become Kuromoji, with the
IPADIC dictionary: besides segmenting, it identifies the part of speech and
provides the reading of each token (normalized to hiragana).
`ScriptJapaneseTokenizer` (segmentation by script type) remains as a
lightweight/offline alternative, without readings. Each `Token` carries the
surface, part of speech, reading and offsets in the tokenized text.

### `reading`

Responsible for obtaining the readings of the tokens.

The main abstraction will be:

```
interface ReadingProvider {
    Optional<Reading> findReading(Token token);
}
```

The reading has priority when it comes from the tokenizer itself (Kuromoji). When
it does not know it, and only for tokens that contain kanji, Jisho is queried as a
fallback:

```
JishoReadingProvider
LocalDictionaryProvider (future)
```

The application must not depend directly on a specific implementation: the chosen
provider is decorated by a `CachingReadingProvider`.

### `reading.cache`

Responsible for storing query results.

Conceptual flow:

```
Token
  │
  ▼
Cache
  │
  ├── found → returns result
  │
  └── not found
           │
           ▼
      ReadingProvider
           │
           ▼
         Cache
```

The cache should avoid repeated queries for the same term.

Current implementation: `CachingReadingProvider` (decorator) + `FileReadingCache`,
with a JSON file that maps surface → reading (default:
`data/cache/readings.json`). The cache is best-effort — read/write failures do not
interrupt processing — and it does not persist terms that are not found.

### `furigana`

Contains the intermediate model used by the rest of the application.

The layer must represent the relationship between the original text and the
reading without assuming how the result will be rendered.

This is important because furigana can later be converted to different formats.

It also centralizes the segmentation of a token into kanji (with reading) and
okurigana (without reading), via `FuriganaSegmenter`, aligning the surface with the
hiragana reading. The final okurigana matches the *end* of the reading, avoiding
confusing an okurigana kana with an equal kana inside the kanji reading (e.g.
`大嫌い` / `だいきらい`).

`FuriganaPipeline` emits, through an optional warning channel, an alert for each
term with kanji that was left without a reading (neither Kuromoji nor the provider
knows it): the entry is kept, but has no furigana.

### `output`

Responsible for generating the output formats.

```
JsonWriter     — FuriganaDocument → JSON
AssGenerator   — FuriganaDocument → ASS events (furigana positioning)
AssWriter      — assembles the final file (template + events, timestamps)
AssTemplate    — header/styles loaded from /ass/default-style.ass
AssLayout      — character grid geometry used in `\pos`
```

`AssGenerator` generates the composition (Strategy 3 from docs/ass-format.md): one
`Main` event per visual line and one `Furigana` event per reading, both positioned
with `{\an5\pos(x,y)}`. It does not know SRT or Jisho.

New formats can be added without changing the earlier layers.

### `mpv`

Responsible for playing the video with the generated furigana subtitle.

Implemented at this stage as a launcher decoupled from the pipeline:

```
MpvPaths        — naming convention of the generated ASS (.furigana.ass)
MpvIntegration  — assembles/runs: mpv --sub-file=<ass> <video>
ProcessRunner   — process execution abstraction (testable)
```

`MpvIntegration` does not know SRT, Jisho or `FuriganaDocument`; it receives only
the paths of the video and the ASS. ASS generation remains in the pipeline and in
the `output` layer, orchestrated by `Application` (`--mpv` mode).

It remains a future scope:

- Lua scripts;
- shortcuts;
- automatic subtitle loading;
- communication with the application;
- dynamic subtitle updates.

The limitations of the current integration are in `docs/mpv.md`.

### `core`

Headless facade shared by every user interface:

```
FuriganaOptions — interface-independent generation options
FuriganaService — process → render JSON/ASS → write (reads the SRT, runs the
                  pipeline, applies the color, embeds/subsets the font)
```

`FuriganaService` contains no user-interface logic: the caller supplies a warning
sink and decides how to present the result. The CLI (`Application`) and the GUI
(`gui`) both go through it, so the pipeline is never duplicated.

### `gui`

Single-window Swing interface (the `gui` entry point, `GuiMain`):

```
GuiMain       — entry point: headless guard, look-and-feel, opens the window
MainWindow    — collects the paths and options, runs the job off the EDT
GuiController — validation + calls FuriganaService (no Swing; unit-testable)
GuiSettings   — small properties-file store (last directory)
```

`GuiController` knows nothing about Swing, so the job logic is tested headlessly.
The controller reuses `FuriganaService` and `MpvIntegration`; the window only
presents fields and messages. The GUI takes no arguments.

## Data flow

The main flow should be:

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
      └──→ AssGenerator
```

## Conceptual model

### Token

Represents a unit identified during tokenization.

Example:

```
surface = "日本語"
```

### Reading

Represents a reading associated with a term.

Example:

```
surface = "日本語"
reading = "にほんご"
```

### FuriganaSegment

Represents a unit that can be rendered.

Conceptual example:

```
baseText = "日本語"
reading = "にほんご"
```

For elements that do not have furigana:

```
baseText = "を"
reading = null
```

## Independence between components

The following dependencies should be avoided:

```
SrtParser → Jisho
SrtParser → ASS
Jisho → SRT
Jisho → MPV
AssWriter → Jisho
```

Instead:

```
SRT → Subtitle
Subtitle → Tokenizer
Tokenizer → Token
Token → ReadingProvider
Token + Reading → FuriganaDocument
FuriganaDocument → Output
```

## Native execution (GraalVM)

Besides the JAR, the project can be compiled as a standalone binary with GraalVM
Native Image (`mvn -Pnative package`). Two binaries are produced from the same
classes: `furigana-parser` (CLI) and `furigana-parser-gui` (GUI, no arguments).
Because the CLI entry point does not reference the GUI, AWT/Swing stays out of the
CLI binary. Because tokenization (Kuromoji) reads the
dictionary as a classpath resource, Jackson serializes the records by reflection
and font subsetting uses JNA/JNI, the native build depends on explicit
configurations, versioned under
`src/main/resources/META-INF/native-image/`:

- `reflect-config.json` — reflection (furigana model, Jackson, JNA);
- `resource-config.json` — resources read at runtime (Kuromoji dictionary,
  default ASS style, JNA's `libjnidispatch`);
- `jni-config.json` / `proxy-config.json` — JNI access and JNA dynamic proxy.

The `native` profile also disables the `maven-shade-plugin`, so that the uber jar
does not duplicate classes and resources in the classpath handed to
`native-image`. The details are in `docs/native-image.md`.

## Decisions still open

The following decisions will be made during implementation:

- handling of words with multiple readings;
- handling of proper nouns;
- dynamic MPV integration (Lua scripts / real-time updates).

Decisions already made:

- tokenization: Kuromoji (IPADIC);
- readings: priority for the tokenizer, with a fallback to Jisho;
- cache: JSON at `data/cache/readings.json`, via `CachingReadingProvider`;
- okurigana: separated inside the token by `FuriganaSegmenter`;
- conjugated verbs: tokenized by Kuromoji (stem + okurigana);
- ASS: Strategy 3 (`\pos` + `Main`/`Furigana` styles, character grid);
- MPV: launcher `mpv --sub-file=<ass>` with an injectable `ProcessRunner`.

These decisions should not be anticipated without need.

## Evolution principle

Each step must produce verifiable output.

For example:

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

This makes it possible to test each stage independently and identify errors
without depending on MPV or the final rendering.
