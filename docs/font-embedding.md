# Font Embedding in the ASS

## Goal

Make the ASS file itself carry the font needed to render the furigana, without
requiring the font to be installed on the viewer's machine.

The problem: the ASS references the font by name (`Noto Sans CJK JP`). If that font
is not installed, the player uses another — or none — and the kanji and the readings
may appear with wrong width and positioning.

The solution: write the font's bytes inside the ASS, in the `[Fonts]` section. The
player registers this font with the system provider and uses it when drawing the
subtitle.

The embedding is done in Java, inside the pipeline. The optional font subsetting
(`--subset-font`) uses the HarfBuzz library, accessed through JNA — with no external
process (see [Font subsetting](#font-subsetting)).

## Font not installed

`Noto Sans CJK` does not ship with Windows, and the template references the family
`Noto Sans CJK JP`. When `--embed-font` is used without an explicit file and the
family is not found in the usual system directories, the program downloads the
official `NotoSansCJKjp-Regular.otf` (≈ 16 MB) from the
[Noto CJK repository](https://github.com/notofonts/noto-cjk), caches it and embeds
that copy — the download happens once, so later runs work offline. Cache locations:

- Windows: `%LOCALAPPDATA%\furigana-parser\fonts`
- macOS: `~/Library/Caches/furigana-parser/fonts`
- Linux: `$XDG_CACHE_HOME/furigana-parser/fonts` (or `~/.cache/furigana-parser/fonts`)

If the download also fails (for example, offline), the error message explains how
to obtain and install the font manually or how to point to a local file with
`--embed-font=<file>`.

## How it works

```text
ASS style (Fontname)
   ↓
Locate the font file on the system
   ↓
[with --subset-font] Subset to the glyphs used (HarfBuzz)
   ↓
Encode the bytes and write the [Fonts] section
```

1. **Locate the font** — `FontLocator` scans the usual system directories
   (Linux, macOS and Windows) and reads the `name` table of each file, comparing the
   family name. The match ignores case and repeated spaces. If the font is inside a
   collection (`.ttc`), only the corresponding face is extracted into a standalone
   font (`SfntReader`), since the ASS embeds one face at a time.
2. **Subset (optional)** — with `--subset-font`, `HarfBuzzSubsetter` keeps only the
   glyphs used by the subtitle. See the next section.
3. **Encode** — `AssFontEmbedder` converts the bytes into the same uuencode format
   as libass: each 3 bytes become 4 six-bit characters, plus 33, in lines of fixed
   size. Line breaks are ignored when reading.
4. **Insert** — the section is written between `[V4+ Styles]` and `[Events]`:

   ```text
   [Fonts]
   fontname: NotoSansJP.otf
   4V254Q!1!1!!"!!!1E&42?X[^29...
   ...
   [Events]
   ```

   `fontname:` is the suggested file name for the font; the following lines are
   the encoded bytes.

## Font subsetting

`Noto Sans CJK JP` has around 65 thousand glyphs (all CJK kanji), but a subtitle
uses only a few hundred. Without subsetting, the embedded ASS ends up with tens of
MB. With `--subset-font`, only the glyphs used are written.

The process:

1. **Collect the code points** — `UsedCodePoints` walks the `FuriganaDocument` and
   gathers the characters of the main text and of the readings (the furigana). The
   override tags (`\pos` etc.) are not glyphs and are not included.
2. **Subset** — `HarfBuzzSubsetter` uses `libharfbuzz-subset` through JNA. The
   functions `hb_subset_*` live in that library; the others (`hb_blob_*`,
   `hb_face_*`, `hb_set_*`) come from `libharfbuzz`, which is its dependency — that
   is why they resolve from the same loaded identifier. Hinting is discarded
   (`HB_SUBSET_FLAGS_NO_HINTING`) and the `name` table is preserved, which keeps the
   family (`Noto Sans CJK JP`) and lets the player match the font to the style's
   `Fontname`.
3. **Embed** — the subset bytes follow the normal encoding and insertion flow.

`AssFontEmbedder` does not know about subsetting: it receives the already subset
`FontFile`. The operation is abstracted by `FontSubsetter`, and the CLI takes care
of invoking it.

### Where the library is looked up

Because the file name varies between systems (on Linux the runtime installation
brings only the versioned soname, e.g. `libharfbuzz-subset.so.0`), the search first
tries JNA's standard resolution and then scans the usual directories
(`/usr/lib/...`, `/usr/local/lib`, Homebrew, etc.). To point to a file explicitly,
use the `FURIGANA_HARFBUZZ_SUBSET` environment variable or the
`-Dfurigana.harfbuzz.subset=<file>` property.

On Windows the subsetting routines (`hb_subset_*`) live in the subset DLL and the
others (`hb_blob_*`, `hb_face_*`, `hb_set_*`) in the core DLL (`libharfbuzz`), which
is a separate file — the search loads the two separately and also scans the PATH and
the usual MSYS2/vcpkg directories. A library that is found but does not expose the
expected symbols is rejected: subsetting then falls back to embedding the full font,
never failing.

If the library is not found — or if subsetting a font fails — the program warns on
`stderr` and embeds the full font. The ASS is never generated without the necessary
glyphs. When the library is missing, the warning already brings the installation
command for the system in use (for example, `sudo apt install libharfbuzz-subset0`
on Debian/Ubuntu or `brew install harfbuzz` on macOS) and the reminder of the
`FURIGANA_HARFBUZZ_SUBSET` variable, used to point to a file explicitly.

## Usage

Without an argument, the font is located by the family name declared in the style
(`Noto Sans CJK JP`):

```
java -jar target/furigana-parser-0.1.0-SNAPSHOT.jar --embed-font input.srt output.ass
```

With an explicit file, the given file is embedded (useful when the font is not in
the usual directories or when you want to use another font):

```
java -jar target/furigana-parser-0.1.0-SNAPSHOT.jar --embed-font=font.otf input.srt output.ass
```

To subset the embedded font to the glyphs used (requires `--embed-font`):

```
java -jar target/furigana-parser-0.1.0-SNAPSHOT.jar --embed-font --subset-font input.srt output.ass
```

The options apply to ASS generation and to `--mpv` mode; in JSON generation they
have no effect (the JSON does not carry a font).

If the family is not found on the system, the program tries to download and cache the
official font automatically (see [Font not installed](#font-not-installed)).

## File size

Without subsetting, the whole file is embedded. `Noto Sans CJK JP` is about
16.5 MB, which produces an ASS of approximately 22 MB.

With `--subset-font`, only the glyphs used are included. As a reference (font
`NotoSansJP.otf`, same family as the style):

| Subtitle | Without subsetting | With `--subset-font` |
| --- | --- | --- |
| `japanese.srt` (3 entries, ~40 characters) | ~22 MB | ~27 KB |
| One episode (~370 entries) | ~22 MB | ~0.3 MB |

The generation time does not change in any relevant way (the subsetting takes a few
milliseconds; the cost lies in tokenizing the subtitle).

## Verification

To confirm that the subtitle uses the embedded font, render the ASS in an
environment without installed fonts (for example, with an empty `fontconfig`):

```
FONTCONFIG_FILE=/path/to/empty-fontconfig ffmpeg -itsoffset <t> \
  -f lavfi -i color=c=black:s=1920x1080:d=0.1 \
  -vf "ass=output.ass,format=gray" -frames:v 1 -f rawvideo -pix_fmt gray out.gray
```

Without the `[Fonts]` section, no subtitle is drawn; with it, the text appears even
without the font installed on the system. The output with and without subsetting is
identical, byte for byte, at the same instant.

## Scope

`AssGenerator` still knows nothing about fonts: the embedding is a post-processing
of the ASS text, triggered by the CLI (`Application`). Locating, subsetting and
encoding are separate steps (`FontLocator`, `FontSubsetter` / `HarfBuzzSubsetter`,
`AssFontEmbedder`), and ASS generation remains independent of those details.

## Tests

The flow of calls to HarfBuzz is covered with a fake implementation of the JNA
interface (`HarfBuzzSubsetterTest`), without requiring the native library. The test
that subsets a real font only runs when `libharfbuzz-subset` and a sample font are
available.

## Future improvements

- **Subsetting without a native library** — a pure Java port (for example, a `glyf`
  subsetter for TrueType fonts) would remove the system dependency, but it does not
  cover CFF/CID such as `Noto Sans CJK JP`.
- **Distributing the library** — packaging `libharfbuzz-subset` per platform would
  make subsetting work without prior installation.
