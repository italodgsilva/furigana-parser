# Graphical interface

## Goal

A single, simple window for people who do not want to use the command line:
choose the input SRT, the output file and the options, and press **Generate**.

The GUI is an alternative front end to the same pipeline as the CLI — it does not
reimplement any processing. Both go through `FuriganaService` (see
[`architecture.md`](architecture.md)).

## Running

The GUI is a separate entry point that takes **no arguments**:

```
java -cp target/furigana-parser-0.1.0-SNAPSHOT.jar com.shinji2324.furigana.gui.GuiMain
```

or, from the release artifacts, the GUI jar and the GUI binary:

```
java -jar furigana-parser-gui-0.1.0.jar
./furigana-parser-gui-0.1.0-linux-x86_64
```

There is one artifact for the CLI and one for the GUI (see the release page):
`furigana-parser-*` runs the command line, `furigana-parser-gui-*` opens the
window. The GUI binary ignores any arguments.

The GUI needs a display. On a headless machine it prints a message and exits; use
the CLI there.

## The window

| Field | Meaning |
| --- | --- |
| **Input (SRT)** | the subtitle to process |
| **Output** | the file to write; the extension (`.ass` or `.json`) chooses the format |
| **Format** | ASS or JSON; only updates the output extension |
| **Video (MPV)** | the video for **Play in MPV** (optional) |
| **Strip parentheses** | removes the passages between round parentheses before furigana |
| **Embed font** | embeds the font into the ASS; an optional file can be chosen, otherwise it is located by family name |
| **Subset font** | subsets the embedded font to the glyphs used (requires **Embed font**) |
| **Color** | primary color of the styles; the picker writes `#RRGGBB` (empty = white) |

- **Generate** processes *Input* and writes *Output*.
- **Play in MPV** processes *Input*, writes `input.furigana.ass` next to the SRT
  and opens *Video* with MPV (it is enabled when a video is chosen).
- **Open folder** reveals the last generated file (using `java.awt.Desktop`).
- The lower area shows the same warnings the CLI prints on `stderr` (missing
  readings, font download, subsetting).

Processing runs off the event dispatch thread, so the window stays responsive
during tokenization, the Jisho lookups, the font download and the subsetting.

## Validation

Before running, the window checks that the input exists, that the output ends in
`.ass` or `.json`, that a video is chosen for MPV and that **Subset font** is not
selected without **Embed font**. The messages seen by the pipeline are collected in
the message area.

## Look-and-feel

The window prefers Nimbus and falls back to Metal when it cannot be initialized —
which happens in the native image, where Nimbus cannot read its font
configuration. See [`native-image.md`](native-image.md).

## Settings

The last directory used in the file choosers is remembered in
`~/.furigana-parser/gui.properties` (a plain properties file, so the native image
does not need the JNI-based preferences API). If the file cannot be read or
written, the GUI simply starts from the user's home directory.
