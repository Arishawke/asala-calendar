# 0008. Natural-language parser vocabulary seam

Date: 2026-06-11

## Status

Accepted.

## Context

The quick-add parser (`ui/eventedit/naturallanguage/`) is a dependency-free
rule-based parser. Its vocabulary was English, with weekday and month maps as
data but the connector and function words (today, tomorrow, next/this, at,
from/to, for, noon/midnight, am/pm, ordinal suffixes, the article, duration
units) baked into the regex literals across `DateGrammar` and `EventTextParser`.
Adding a language would have meant editing the grammar files directly.

The owner anticipates possibly adding Western-European languages (French, German,
Spanish, Italian), which share the parser's structural assumptions: Latin script,
spaces between words, prepositions before nouns, and predominantly 24-hour time
(handled by the universal HH:mm path). For that target the "swap the vocabulary,
keep the grammar" model holds, so collecting the vocabulary into one place is
worthwhile insurance. No second language is built yet.

## Decision

All language-specific words live in `Vocabulary` (`naturallanguage/Vocabulary.kt`),
grouped by grammatical role. `Vocabulary.English` is the only instance today.
`Vocabulary.forLocale(locale)` is the single extension point and returns English
for every locale; a future language becomes one branch keyed on `locale.language`.
Its `locale` parameter is deliberately unused until that branch exists and carries
a `@Suppress("UnusedParameter")` to mark the intent rather than hide it in the
detekt baseline.

`EventTextParser.parse(input, now, locale)` keeps its signature, resolves
`forLocale(locale)`, and threads the vocabulary into the grammar. `DateGrammar`
builds its regexes from the vocabulary via a longest-first `alt()` helper.
Numeric date order stays driven by `locale` (`isMonthFirst`), separate from the
word tables, because it is a date-format fact, not vocabulary.

This was a behavior-preserving refactor: the existing unit suite stayed green and
unedited, which is the proof that English behavior did not change. One test was
added pinning that an unsupported locale falls back to English.

## Consequences

- Localizing to a Western-European language is now a vocabulary swap plus a branch
  in `forLocale`, with the grammar untouched.
- The seam makes the vocabulary swappable, not the grammar. These English-shaped
  assumptions remain and a real second language must address them:
  - Ordinal suffixes work as a token list, but their grammar is English (French
    "1er", German "3." need more than a suffix swap).
  - am/pm is structural and its semantic mapping in `clock()` compares literal
    "am"/"pm"; harmless for 24-hour-default European locales that fall through to
    the HH:mm path.
  - `alt()` does not regex-escape tokens, because every English token is plain
    ascii and escaping would interact with IGNORE_CASE. A future token containing
    a regex metacharacter would need escaping added here.
  - `\b` word boundaries get fussy around accented characters; revisit when a
    language with diacritics in its keywords lands.
- No user-visible change, so no CHANGELOG entry. The parser remains English-only.
