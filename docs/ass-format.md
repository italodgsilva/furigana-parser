# ASS Format and Furigana

## Goal

The ASS (Advanced SubStation Alpha) format is used to produce subtitles with
greater control over positioning and formatting.

The goal is to visually represent something similar to:

```text
   きょう       にほんご
今日は日本語を勉強します。
```

instead of simply:

```text
今日は日本語を勉強します。
（きょう）（にほんご）
```

## Why ASS?

SRT has a simple structure and little capacity for visual control.

ASS allows controlling properties such as:

- position;
- font;
- size;
- color;
- alignment;
- style;
- spacing;
- text effects;
- individual positioning of elements.

This makes it more suitable for a furigana representation.

## Important

ASS must not be treated as part of the logic for obtaining readings.

The architecture is:

```text
Japanese
   ↓
Tokenization
   ↓
Readings
   ↓
FuriganaDocument
   ↓
AssGenerator
   ↓
ASS
```

`AssGenerator` receives an already processed document. It does not query Jisho nor
know SRT or tokenizers.

## Visual model

Consider:

```text
日本語
にほんご
```

The desired representation is:

```text
にほんご
日本語
```

with the reading positioned above the main text.

For a sentence:

```text
今日は日本語を勉強します。
```

we can conceptually split the line into:

```text
今日 | は | 日本語 | を | 勉強 | します | 。
```

and render only the segments that have a reading:

```text
  きょう             にほんご       べんきょう
今日は              日本語        勉強 します。
```

## Possible strategies

### Strategy 1 — two lines

Generate two lines of text:

```text
furigana
main text
```

It is relatively simple, but it has alignment problems when there are several
segments of different sizes.

### Strategy 2 — per-segment positioning

Each segment is rendered separately:

```text
[今日] [は] [日本語] [を] [勉強] [します]
  ↑             ↑            ↑
furigana      furigana     furigana
```

It offers more control, but increases the complexity of the generator.

### Strategy 3 — ASS tags and positioning

It uses positioning tags and ASS styles to build the composition directly inside
the subtitle.

## Adopted strategy

**Strategy 3** was adopted, combined with the idea of Strategy 2: per-segment
positioning via ASS tags.

`AssGenerator`:

- emits one event per **visual line** of the main text, in the `Main` style,
  centered with `{\an5\pos(x,y)}`;
- emits an independent furigana event, in the `Furigana` style, for each segment
  with a reading, positioned with `{\an5\pos(x,y)}` above the corresponding kanji.

Since ASS has no native ruby, the horizontal coordinate of the furigana is
calculated from a **character grid** (`AssLayout`): each full-width character
(kanji, kana, Japanese punctuation) occupies one "em", half-width katakana occupies
half an "em", and ASCII characters use the **real advance** of the template font
(`ASCII_ADVANCES`, extracted from `hmtx`). The CJK font's Latin is proportional —
the space measures `0.224` em, the `(` measures `0.338` em —, far from a fixed
half-em width, and ignoring this shifts the furigana cumulatively along the line.

The "em" is **not** the font size declared in the ASS: libass (used by MPV)
interprets `Fontsize` as the font's line height (`hhea`), so the real advance of a
full-width character is `Fontsize * EM_PER_FONT_SIZE`. For the template font
(`Noto Sans CJK JP`), `EM_PER_FONT_SIZE = 1000/1448 ≈ 0.6906`. The vertical
coordinates of the main text use the libass line box (which has exactly the height
of `Fontsize`); the furigana is positioned by the "em" (the glyph boxes,
`CELL_WIDTH` and `FURIGANA_FONT_SIZE * EM_PER_FONT_SIZE`), with a small gap
(`FURIGANA_GAP`) — this font's line box has a large internal "leading", which would
push the reading away from the kanji if used as a reference.

The original text and synchronization are always preserved. The association between
segments and visual lines is done by character offset in the original text, which
works both with tokenizers that preserve line breaks as tokens (Kuromoji) and with
those that discard them (`ScriptJapaneseTokenizer`).

The geometric constants (`AssLayout`) assume the 1920x1080 resolution and the
default template font; changing the `PlayRes` or the font requires adjusting these
constants (in particular `EM_PER_FONT_SIZE` and the `ASCII_ADVANCES` table).

## Styles

The ASS styles are kept separate from the generation logic: they live in the
resource `src/main/resources/ass/default-style.ass`, which also defines
`[Script Info]`, the resolution and the `[Events]` header. `AssGenerator` only
appends the events.

Current styles:

```text
Main       — main text (white, 64px)
Furigana   — reading (white, 32px), displayed above the main text
```

This makes it possible to change the font, size, color, margin and style without
modifying the logic responsible for composing the text.

## Synchronization

The original subtitle timestamp is preserved.

If an SRT entry has:

```text
00:01:12,500 --> 00:01:15,200
```

the corresponding ASS entry uses the same time interval (`0:01:12.50` →
`0:01:15.20`). The process of adding furigana does not change the synchronization.

## Japanese characters

The generator fully preserves:

- kanji;
- hiragana;
- katakana;
- Japanese punctuation;
- relevant spaces;
- Unicode characters present in the subtitle.

The output encoding is UTF-8.

## Open questions

Points that may still need refinement:

- characters outside ASCII and CJK (e.g. accented Latin letters, other alphabets)
  still use one "em" by default, with no real measurement;
- kerning between glyph pairs is not considered;
- collision between lines with furigana in texts with many lines;
- very long texts (automatic wrapping).

## Conceptual transformation example

Input:

```text
今日は日本語を勉強します。
```

Intermediate model:

```text
今日       → きょう
は         → -
日本語     → にほんご
を         → -
勉強       → べんきょう
します     → -
。         → -
```

Actual ASS output (excerpt):

```text
Dialogue: 0,0:00:01.00,0:00:04.50,Main,,0,0,0,,{\an5\pos(960,988)}今日は日本語を勉強します。
Dialogue: 0,0:00:01.00,0:00:04.50,Furigana,,0,0,0,,{\an5\pos(717,951)}きょう
Dialogue: 0,0:00:01.00,0:00:04.50,Furigana,,0,0,0,,{\an5\pos(872,951)}にほんご
Dialogue: 0,0:00:01.00,0:00:04.50,Furigana,,0,0,0,,{\an5\pos(1026,951)}べんきょう
```

## Embedded font

The ASS can carry the font itself in the `[Fonts]` section, so there is no need to
install it on the machine displaying the subtitle. The CLI's `--embed-font` option
does this embedding by locating the style's font by family name and writing the
bytes before the `[Events]` section. With `--subset-font`, the font is subset to
the glyphs used in the subtitle (via HarfBuzz) before being embedded, which greatly
reduces the file size. See
[`font-embedding.md`](font-embedding.md).

## Relation to MPV

MPV will be responsible only for playing and displaying the ASS file.

The generation pipeline is independent:

```text
SRT
 ↓
Furigana Parser
 ↓
ASS
 ↓
MPV
```

Later, a more dynamic integration could be created, in which MPV uses scripts to
request or update the readings during playback.

That integration is outside the initial scope.
