# Furigana Parser

[![CI](https://github.com/shinji2324/furigana-parser/actions/workflows/ci.yml/badge.svg)](https://github.com/shinji2324/furigana-parser/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/shinji2324/furigana-parser)](https://github.com/shinji2324/furigana-parser/releases)

Tool for processing Japanese subtitles, with the goal of adding furigana readings
to the kanji and generating subtitles compatible with players such as MPV.

The project is being developed incrementally, keeping subtitle processing separate
from rendering and player integration.

## Goals

The main goal is to turn an ordinary Japanese subtitle into a structured
representation containing the readings of the Japanese terms.

Flow:

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
 ├── Kuromoji (primary)
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

## Current state

The project is under incremental development, with verifiable output at the end of
each step.

Completed steps:

1. SRT parser (fail-fast on malformed blocks, optional indices, timestamps preserved as a raw string).
2. Internal furigana model — `FuriganaDocument` → `FuriganaEntry` (id, start, end, text, segments) → `FuriganaSegment` (text + reading).
3. Japanese text tokenization — `JapaneseTokenizer` interface, with the provisional `ScriptJapaneseTokenizer` implementation (by script type).
4. SRT → intermediate JSON pipeline, orchestrated by `FuriganaPipeline` and runnable from the CLI (`Application`).
5. Morphological tokenization with Kuromoji (IPADIC dictionary), with readings in hiragana and kanji/okurigana separation (`FuriganaSegmenter`).
6. External readings: a real `JishoReadingProvider`, mediated by `CachingReadingProvider` with an on-disk cache (`data/cache/readings.json`).
7. ASS generation with positioned furigana (`AssGenerator` + `AssWriter` + `AssLayout`), with styles in `src/main/resources/ass/default-style.ass`.
8. MPV integration (`--mpv`): generates the ASS next to the SRT and opens the video with `mpv --sub-file=...` (see `docs/mpv.md`).
9. Font embedding into the ASS itself (`--embed-font`): locates the style's font by family name, extracts the face from `.ttc` collections and writes the `[Fonts]` section, all in Java. With `--subset-font`, it subsets the embedded font to the glyphs used in the subtitle, using HarfBuzz (see `docs/font-embedding.md`).
10. Text cleanup (`--strip-parentheses`): removes the passages between round parentheses (full-width and half-width) before tokenization, discarding entries that become empty.
11. Automatic release publishing: a `v*` tag triggers the release workflow, which builds the JARs (CLI and GUI) and attaches them to a GitHub Release (see the "Publishing a release" section).
13. Color customization (`--color`): changes the primary color of the `Main` and `Furigana` styles, accepting a name, `#RRGGBB` or an ASS `AABBGGRR` value. Furigana that would overlap on the same line is spread apart automatically.
14. Graphical interface (`gui`): a single window to choose the input/output and the options, with a color picker and a "Play in MPV" button. It is a separate entry point (`furigana-parser-gui` binary and `-gui.jar`, no arguments) that reuses the same pipeline through `core.FuriganaService` (see [`docs/gui.md`](docs/gui.md)).

Next steps (outside the current scope):

- dynamic MPV integration (Lua scripts, real-time updates).

## Technologies

- Java 21
- Maven
- JUnit 5
- Jackson
- Kuromoji (morphological tokenization, IPADIC dictionary)
- Swing (graphical interface, on top of the same pipeline)
- SRT format
- ASS/SSA format
- MPV
- Jisho, as a fallback source for readings

## Project structure

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
│   │   │       ├── core/
│   │   │       ├── cli/
│   │   │       ├── gui/
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
│   │       ├── com/shinji2324/furigana/gui/awt/
│   │       │   ├── fontconfig-windows.properties
│   │       │   └── fontconfig-macos.properties
│   │       └── META-INF/
│   │               └── com.shinji2324/furigana-parser/
│   │                   ├── reflect-config.json
│   │                   ├── resource-config.json
│   │                   ├── jni-config.json
│   │                   ├── proxy-config.json
│   │
│   └── test/
│       ├── java/
│       └── resources/
│
├── data/
│   ├── cache/
│   └── output/
│
├── docker/
│   ├── Dockerfile.jar
│   └── Dockerfile.trace
│
│   ├── gui/                        # traced with the tracing agent (Linux)
│   │   ├── reflect-config.json
│   │   ├── jni-config.json
│   │   └── resource-config.json
│   └── awt/                        # generated from the OpenJDK sources
│       └── jni-config.json
│
├── tools/
│   └── generate-awt-jni-config.py
│
└── docs/
    ├── architecture.md
    ├── ass-format.md
    ├── font-embedding.md
    ├── gui.md
    ├── mpv.md
```

## Download and usage

Each published version leaves the JAR and the ready-made binaries on the
[Releases](https://github.com/shinji2324/furigana-parser/releases) page. Download
the file for your platform:

| Platform | File |
| --- | --- |
| Linux x86-64 | `furigana-parser-<version>-linux-x86_64` |
| Linux arm64 | `furigana-parser-<version>-linux-aarch64` |
| macOS Apple Silicon | `furigana-parser-<version>-macos-aarch64` |
| macOS Intel | `furigana-parser-<version>-macos-x86_64` |
| Windows x86-64 | `furigana-parser-<version>-windows-x86_64.exe` |
| Any system (requires Java 21+) | `furigana-parser-<version>.jar` |

Every file above is the command-line version. Each also has a graphical
counterpart — the same name with `gui` after `furigana-parser`, e.g.
`furigana-parser-gui-<version>-linux-x86_64.tar.gz` or
`furigana-parser-gui-<version>.jar`. The GUI binaries and the GUI jar take **no
arguments**: running them opens the window (see [`docs/gui.md`](docs/gui.md)).

The JAR needs Java 21 or higher.
libraries shipped next to the executable — extract it before running.

On Linux and macOS, mark the binary as executable before running:

```
chmod +x furigana-parser-0.2.0-linux-x86_64
./furigana-parser-0.2.0-linux-x86_64 input.srt output.ass
```

On Windows, just run the `.exe` (the first time, SmartScreen may ask for
confirmation). On macOS, if Gatekeeper blocks the unsigned binary, allow it with
`xattr -d com.apple.quarantine <file>` or under System Settings → Privacy &
Security.

Each Release includes a `SHA256SUMS.txt` to verify the integrity of the downloads:

```
sha256sum -c SHA256SUMS.txt
```

### The subtitle font

The generated ASS references the family `Noto Sans CJK JP`. If that font is not
installed, the player (e.g. MPV) falls back to another font and the furigana may
lose alignment. There are two ways to guarantee the font:

- **Let the program download it (recommended):** with `--embed-font` and no
  argument, the program locates the font by family name; if it is not installed,
  it downloads the official `NotoSansCJKjp-Regular.otf` (≈ 16 MB) once, caches it
  and embeds it. The ASS then carries the font itself, so the viewer's machine does
  not need it installed.
- **Install the font:** download `NotoSansCJKjp-Regular.otf` from the
  [official Noto CJK repository](https://github.com/notofonts/noto-cjk) and install
  it:
  - Windows: double-click the file → **Install** (or copy it to `C:\Windows\Fonts`);
  - macOS: double-click → **Install Font** (Font Book);
  - Linux: copy it to `~/.local/share/fonts` and run `fc-cache -f`.

The downloaded file is cached per operating system:
`%LOCALAPPDATA%\furigana-parser\fonts` (Windows),
`~/Library/Caches/furigana-parser/fonts` (macOS) and
`$XDG_CACHE_HOME/furigana-parser/fonts` or `~/.cache/furigana-parser/fonts` (Linux).

The full list of options is in [Command line](#command-line).

## Development

Requirements:

- JDK 21 or higher
- Maven 3.9 or higher

Compile:

```
mvn clean compile
```

Run the tests:

```
mvn test
```

Build the JAR (with the dependencies bundled in, e.g. Jackson and Kuromoji):

```
mvn package
```

It produces two executable jars: the CLI (`furigana-parser-<version>.jar`) and the
GUI (`furigana-parser-<version>-gui.jar`):

```
java -jar target/furigana-parser-0.2.0-SNAPSHOT.jar --help
java -jar target/furigana-parser-0.2.0-SNAPSHOT-gui.jar
```

The output is `target/furigana-parser` (CLI, a single file) and
`target/furigana-parser-gui` (GUI, no arguments; it needs the AWT libraries that

### Publishing a release

Versions are published automatically by the
[`release.yml`](.github/workflows/release.yml) workflow: just create and push a
`v*` tag.

```
git tag v0.2.0
git push origin v0.2.0
```

binaries (CLI and GUI) for Linux (x86-64 and aarch64), macOS (x86-64 and arm64) and
Windows, and attaches everything to a GitHub Release named after the tag, along with
a `SHA256SUMS.txt`. It also works when you create the Release through the GitHub
interface: the created tag triggers the workflow, which attaches the files to the
existing Release.

The version number in the file names comes from the tag (without the `v`), so
`v0.2.0` produces `furigana-parser-0.2.0.jar`, `furigana-parser-gui-0.2.0.jar`,
`furigana-parser-0.2.0-linux-x86_64`, `furigana-parser-0.2.0-windows-x86_64.exe`
and, as the GUI archive, `furigana-parser-gui-0.2.0-linux-x86_64.tar.gz` (`.zip` on
Windows).

### Command line

The output format is chosen by the file extension: `.json` produces the
intermediate model and `.ass` produces the subtitle with positioned furigana.

In the examples below, replace `java -jar target/furigana-parser-0.2.0-SNAPSHOT.jar`
with the path to the downloaded JAR (e.g. `java -jar furigana-parser-0.2.0.jar`) or
to the JAR.

```
java -jar target/furigana-parser-0.2.0-SNAPSHOT.jar input.srt output.json
java -jar target/furigana-parser-0.2.0-SNAPSHOT.jar input.srt output.ass
java -jar target/furigana-parser-0.2.0-SNAPSHOT.jar --embed-font input.srt output.ass
java -jar target/furigana-parser-0.2.0-SNAPSHOT.jar --embed-font=font.otf input.srt output.ass
java -jar target/furigana-parser-0.2.0-SNAPSHOT.jar --embed-font --subset-font input.srt output.ass
java -jar target/furigana-parser-0.2.0-SNAPSHOT.jar --strip-parentheses input.srt output.ass
java -jar target/furigana-parser-0.2.0-SNAPSHOT.jar --color=yellow input.srt output.ass
java -jar target/furigana-parser-0.2.0-SNAPSHOT.jar --color=#1122FF input.srt output.ass
java -jar target/furigana-parser-0.2.0-SNAPSHOT.jar --mpv input.srt video.mkv
java -jar target/furigana-parser-0.2.0-SNAPSHOT.jar --help
```

`java -jar target/furigana-parser-0.2.0-SNAPSHOT.jar` with `./target/furigana-parser`.

With `--embed-font`, the ASS starts carrying the font itself in the `[Fonts]`
section, so there is no need to install the font on the viewer's machine. The
option also works in `--mpv` mode. Without an argument, the font is located by the
style's family name (`Noto Sans CJK JP`); with `--embed-font=<file>`, the given
file is used. Because the whole file is embedded, the ASS grows considerably
(around 22 MB with `Noto Sans CJK JP`). When the family is not installed on the
machine generating the ASS, the program downloads and caches the official font
automatically (see [The subtitle font](#the-subtitle-font)).

With `--subset-font` (which requires `--embed-font`), only the glyphs used in the
subtitle are written, which shrinks the file drastically (a whole episode comes to
around 300 KB). The subsetting uses HarfBuzz (`libharfbuzz-subset`); if the
library is not available, the program warns (with the installation suggestion for
your system) and embeds the full font. On Windows the program loads the core and
subset DLLs separately (scanning the PATH and the usual MSYS2/vcpkg directories)
so subsetting works when HarfBuzz is installed.
See [`docs/font-embedding.md`](docs/font-embedding.md).

With `--strip-parentheses`, the passages between round parentheses — full-width
(`（…）`) or half-width (`(…)`) — are removed from the text before tokenization.
This avoids generating furigana (and looking up readings) for character names,
reactions and scene directions (e.g. `（笑）`, `(Taro)`). If an entry becomes
entirely empty, such as a line that was only `（笑）`, it is discarded. The option
applies to JSON, ASS and `--mpv`.

With `--color`, the primary color of both styles (`Main` and `Furigana`) is
replaced. The value may be one of the names `yellow`, `gold`, `orange`, `red`,
`white`, `cyan`, `magenta`, `lime`, `blue`, `black`, a `#RRGGBB` (or `RRGGBB`)
value, or an ASS `AABBGGRR` value. An unrecognized value leaves the default
(white) untouched. The option applies to ASS and `--mpv`. Independently of the
color, furigana readings that would overlap on the same line are spread apart
before the ASS is written.

In `--mpv` mode, the ASS is generated next to the SRT (`input.furigana.ass`) and
the video is opened in MPV with `--sub-file`. It requires MPV installed on the
`PATH`. The limitations of this integration (fixed resolution, CJK font, subtitle
tracks etc.) are in [`docs/mpv.md`](docs/mpv.md).

The generated JSON has the following structure (each token is split into kanji,
with a reading, and okurigana, without a reading):

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

`id`, `start` and `end` preserve the original SRT synchronization. The readings
come from Kuromoji and, when it does not know a term with kanji, from Jisho
(mediated by a cache at `data/cache/readings.json`). Entries without kanji are not
discarded, so as not to break the subtitle synchronization. When no source knows a
term with kanji, it is left without furigana and a `Warning:` message is emitted on
`stderr`.

The generated ASS (the `\pos` strategy described in `docs/ass-format.md`) uses two
styles — `Main` for the text and `Furigana` for the readings — and positions each
reading above the corresponding kanji:

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

### Graphical interface

Instead of the command line, the same operations can be done in a window (the
`furigana-parser-gui` binary or the `-gui.jar`). It takes no arguments: running it
opens the window, where you choose the input SRT, the output file and the options
(including a color picker), and press **Generate**. The **Play in MPV** button needs
the video and opens it with the subtitle generated next to the SRT. The warnings the
CLI prints on `stderr` appear in the message area of the window. See
[`docs/gui.md`](docs/gui.md).

## Project principles

### Separation of responsibilities

The subtitle parser must not know about Jisho.

The `ReadingProvider` must not know about the SRT format.

The ASS generator must not depend directly on Jisho.

Each step must work with well-defined internal models.

### Intermediate format

The project uses an intermediate representation between obtaining the readings and
generating the output.

This makes it possible to generate different formats from the same data:

```
FuriganaDocument
    │
    ├── JSON
    ├── ASS
    └── other formats in the future
```

### Cache

External queries should be cached whenever possible.

This reduces:

- the number of requests;
- processing time;
- dependence on the service's availability;
- the chance of repeating unnecessary queries.

The reading cache is written to `data/cache/readings.json` (a JSON object mapping
surface → reading). Terms that are not found are not persisted, so they can be
queried again in future runs. Processing works offline: if Jisho is unavailable,
the missing readings stay `null`.

## Conceptual example

A subtitle such as:

```
今日は日本語を勉強します。
```

is represented internally in a form similar to:

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

## License

This project is distributed under the [MIT License](LICENSE).

The two font mappings under `src/main/resources/com/shinji2324/furigana/gui/awt/`
are the ones OpenJDK ships, copied verbatim and kept under their original
GPLv2-with-Classpath-exception license.
