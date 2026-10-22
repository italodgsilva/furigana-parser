# Reading hints from parentheses in SRT

This document describes the planned behavior for extracting explicit reading hints
written as `漢字(よみ)` / `漢字（よみ）` from the original SRT and applying them
with highest priority when generating furigana.

## Rationale
- Many fansubs annotate character names/readings with `(よみ)` in the original SRT.
- Kuromoji/IPAdic often fails to guess invented or uncommon proper names.
- Extracting these hints is deterministic and avoids manual global dictionaries.
- A post-process agent can cover remaining cases later, but this handles the
  majority directly.

## Extraction rules
- Match patterns `base(よみ)` and `base（よみ）` where `base` contains at least
  one kanji.
- The reading group should be primarily hiragana/katakana. Readings are
  normalized to hiragana (katakana → hiragana) to match the tokenizer format.
- Ignore common parentheticals like `(笑)`, `(泣)`, `(SE)`, etc. (only treated as
  hints when the base has kanji).
- Spaces around parentheses are tolerated.
- First occurrence of a given base wins (deterministic). Later occurrences do not
  overwrite by default.

## Priority and interaction
- **Highest priority**: hints override the tokenizer reading and override any
  reading provider (Jisho/cache). In `FuriganaPipeline.resolveReading`, hints are
  checked before `token.hasReading()`.
- **Parentheses removal**:
  - Hint parentheses are removed from the processed text so they do not appear
    in the output.
  - Hint parentheses are removed even if `--strip-parentheses` is disabled.
  - Other parentheticals are still handled by `ParenthesisStripper` when
    `--strip-parentheses` is enabled (and entries that become blank are
    discarded as before).
- **Extraction timing**: hints are extracted from the original SRT content/entry
  text before stripping. They are removed from the text passed to tokenization.

## Implementation plan
1. **Create `SrtReadingHintExtractor`** (`src/main/java/com/shinji2324/furigana/reading/`)
   - `extractHints(String text) -> Map<String,String>` (base → normalized hiragana)
   - `removeHintParentheses(String text) -> String`
   - Normalize katakana to hiragana; require kanji in base
2. **Create `HintReadingProvider`** (`src/main/java/com/shinji2324/furigana/reading/`)
   - `ReadingProvider` backed by a mutable hints map
   - Returns reading with source `"hint"`
3. **Modify `FuriganaPipeline`**
   - Add mutable `hintsMap` and `HintReadingProvider` (highest priority)
   - In `process(String)`, clear hints, extract from original, remove hint parens
     from content before parsing
   - In `build(Subtitle)`, defensively extract/merge hints from entry texts and
     remove hint parens before applying `stripParentheses`
   - In `resolveReading(Token)`, check hints first (before tokenizer reading)
4. **No changes required** to `FuriganaService` or `Application` (pipeline handles
   extraction/removal)

## Edge cases
- Same base with different hints: first occurrence wins (configurable later if
  needed)
- Hints on tokenized boundaries: removal happens on raw text so tokens align
  after parens are deleted
- Readings containing long vowel marks, small kana, or mixed forms are
  normalized conservatively
- Hint parens nested inside other parens are rare; the pattern targets the
  specific `base(reading)` form with non-parenthesis reading content
