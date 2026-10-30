# Passphrase generator

Status: IN_REVIEW
Backlog ID: 16

## Goal
The password generator gets a "Passphrase" toggle (off by default). It produces a Diceware-style phrase of random English words, e.g. `Cobalt-Unpaved-Scenic-Ladle-Vowel-Thriving`. The phrase is longer and easier to remember than a random password, and the sheet states its strength in bits.

## Scope
- In:
  - A pure `PassphraseGenerator` in `util/`, which draws words uniformly with the injected `SecureRandom`.
  - A bundled EFF large wordlist: 7,776 words, about 12.925 bits per word.
  - Options:
    - word count: 4–10, default 6
    - separator: `-` (default), space, `.`, `_`
    - "Capitalise words" (off by default)
    - "Add a digit" (off by default)
  - The strength for the passphrase preview comes from its true entropy, not from the character-class estimate.
  - A one-line attribution for the wordlist in the sheet, plus an entry in `README.md`.
- Out:
  - User-chosen or custom wordlists, other languages, the EFF short lists.
  - Remembering the last mode or options across sheet openings.
  - A minimum-length rule tied to the master password.
  - Changing the char-class `PasswordStrength.of` scorer that the Add/Edit field uses after "Use".

## Acceptance criteria
- [ ] The generator sheet opens in password mode as before. The Passphrase switch is off.
- [ ] Turning Passphrase on:
  - shows a 6-word, `-`-separated, lowercase preview
  - hides the length slider and the character-class switches
  - shows a word-count slider (4–10), a separator choice, "Capitalise words" and "Add a digit"
- [ ] The word-count slider changes the number of words in the preview (4 and 10 checked). Regenerate gives a different phrase.
- [ ] Each separator choice appears between the words. "Capitalise words" capitalises the first letter of every word. "Add a digit" puts exactly one digit 0–9 directly after one of the words.
- [ ] The strength label shows the bits, with each level reached by these settings:
  - 4 words → about 51 bits, Fair
  - 6 words → about 77 bits, Good
  - 7 words → about 90 bits, Strong
- [ ] The attribution "Wordlist: EFF (CC BY 3.0)" is visible in passphrase mode.
- [ ] "Use" fills the password field with the exact phrase. After Save, the revealed credential matches it.
- [ ] Turning Passphrase off restores the previous password options and preview behaviour.
- [ ] The first switch to passphrase mode doesn't freeze the UI. The list loads off the main thread, and the sheet shows a short loading state if the list isn't ready yet.

## Design notes
- Affected files/layers:
  - New `app/src/main/res/raw/eff_large_wordlist.txt`. Keep the original EFF file unmodified (`<5 dice digits>\t<word>` per line). It is about 108 KB.
    - Why a raw resource: it gets a typed `R.raw` id and needs no asset-path strings. `src/main/resources` (Java classpath) was rejected because it is not the Android convention.
  - New `util/PassphraseGenerator.kt`, shaped like `PasswordGenerator`:
    - `PassphraseGenerator(random: SecureRandom, words: List<String>)` with `generate(PassphraseOptions): String`.
    - `PassphraseOptions(wordCount = 6, separator = Separator.Hyphen, capitalise = false, addDigit = false)`. Constants `MIN/MAX/DEFAULT_PASSPHRASE_WORDS` (4/10/6).
    - `Separator` enum: Hyphen, Space, Period, Underscore, each with its `value`.
    - `fun PassphraseOptions.entropyBits(listSize: Int): Double`:
      - `wordCount × log2(listSize)`
      - plus `log2(10) + log2(wordCount)` when a digit is added (a random digit after a random word)
      - capitalise and separator add 0 bits, because the user chooses them and they are not random
    - A specific error: add `WordCountOutOfRange` to the generator's error type, matching `GeneratorOptionsException.LengthOutOfRange`. Either extend that sealed class or add a sibling sealed `PassphraseOptionsException : IllegalArgumentException`. The developer picks one and logs the choice.
  - New `util/Wordlist.kt`:
    - `fun parseWordlist(input: InputStream): List<String>` takes the second tab field of each non-blank line. It throws a specific `WordlistException` when the line count isn't 7,776, a word is blank, or a word is duplicated.
    - This keeps parsing pure and testable on the JVM. The Android side only supplies `resources.openRawResource(R.raw.eff_large_wordlist)`, closed with `use {}`.
  - Loading:
    - Load once in `AppContainer` as a `suspend` lazy value: a `Mutex` or a `CoroutineScope.async(start = LAZY)` on the app scope, run on `Dispatchers.IO`. Repeat openings of the sheet don't parse it again. The list is not secret, so caching it in memory for the process lifetime is fine.
    - The sheet gets the loader via a parameter or a `LocalAppContainer`, whichever the existing composables already use; follow the existing pattern. It calls the loader from `LaunchedEffect` when passphrase mode turns on.
    - A failed load shows an inline error and keeps password mode usable. It is not expected with a bundled file.
  - `util/PasswordStrength.kt`: expose the band mapping as `PasswordStrength.fromBits(bits: Double)`. `of(password)` then reuses it.
    - Bands stay 40/60/80. Don't loosen them for passphrases: 6 words showing Good is the honest result.
  - `presentation/components/GeneratorSheet.kt`:
    - Passphrase `OptionRow` toggle at the top.
    - Mode-specific controls, using the existing `update` regenerate-on-change pattern.
    - `PasswordStrengthIndicator(strength = …)`. The overload already exists. Add an optional bits text such as "Strength: Good · 77 bits", shown only in passphrase mode or in both modes; developer's choice, keep it minimal.
    - The title stays "Generate password".
  - `README.md`: a credits line, "EFF Large Wordlist for passphrases, © Electronic Frontier Foundation, CC BY 3.0 US, https://www.eff.org/dice".
- Data model / migration changes: none.
- Security considerations:
  - A uniformly random passphrase is not weaker by nature. Its strength is exactly `words × log2(7776)` bits, and that holds even when the attacker knows the list and the method (Kerckhoffs's principle). "Meaningful English" refers only to the alphabet. The phrase is not chosen by a person or built from a sentence, so dictionary attacks gain nothing beyond the computed entropy.
  - The UI states the cost honestly, in bits:
    - 6 words ≈ 77.5 bits, about a random 13-character all-class password. This is EFF's recommended default.
    - The default 20-character random password is ≈ 131 bits.
    - For the master password, 7 or more words (≥ 90 bits) are better, because they guard an offline Argon2id attack.
  - Only `SecureRandom.nextInt(bound)` is used: it has no modulo bias, and it is the same instance pattern as `PasswordGenerator`. Never use `kotlin.random`. The digit position and the digit value come from the same `SecureRandom`.
  - Never reject or regenerate phrases by content (for example "avoid this word"). Filtering shrinks the space and breaks uniformity.
  - Don't log previews. The sheet stays `SecureOn`.
  - Known gap: after "Use", the Add/Edit field indicator still uses the char-class estimate. A 6-word lowercase phrase of about 40 characters scores Strong there. This is acceptable (it over-rates), and it is noted for the verifier. Fixing it would need the field to know where the value came from, which is out of scope.
- Licence: the EFF wordlists are published on eff.org under CC BY 3.0 US. Attribution is required: credit EFF and link the licence. Redistribution in an app is allowed, and the licence is compatible with this project's dependencies. The README credit and the sheet caption cover it.

## Critical logic needing unit tests
- `PassphraseGeneratorTest`, using a seeded `SHA1PRNG` like `PasswordGeneratorTest` plus a small synthetic list such as `w0..w15`:
  - The word count equals the requested count at 4, 6 and 10. 3 and 11 throw `WordCountOutOfRange`.
  - Uniform selection: over about 160,000 draws from a 16-word list, every word appears, and a chi-square test (or each count within ±5% of the expected value) passes. Print the counts on failure.
  - Use a fixed-sequence fake `SecureRandom` subclass (override `nextInt(bound)`) to check that word *i* is `words[draw_i]`. This proves the draw maps straight to an index.
  - Every separator appears exactly `wordCount − 1` times, and the words contain no separator characters.
  - With capitalise on, each word's first character is uppercase and the rest is unchanged. With it off, all characters are lowercase.
  - With "add a digit" on, there is exactly one digit, at the end of one word. Over many runs every digit 0–9 and every word position shows up. With it off, there are no digits.
- Entropy:
  - `entropyBits(7776)`: 6 words → 77.55 ± 0.01. 4 words → 51.70.
  - With a digit at 6 words → 77.55 + log2(10) + log2(6).
  - Capitalise and separator don't change the bits.
  - `PasswordStrength.fromBits` returns the expected band at the 40/60/80 boundaries. Confirm `of(password)` is unchanged, which the existing `PasswordStrengthTest` already covers.
- `WordlistTest`:
  - Parse the real `app/src/main/res/raw/eff_large_wordlist.txt` as a file from the module directory, so no Robolectric is needed:
    - exactly 7,776 words
    - no duplicates
    - every word non-blank, lowercase, and without whitespace or separator characters
  - Synthetic streams: a wrong count, a duplicate, or a blank word throws `WordlistException`.

## Dependencies
- 04 (`PasswordGenerator`, `PasswordStrength`, `GeneratorSheet`, `PasswordStrengthIndicator`).
- `AppContainer` / app `CoroutineScope` (D4) for the cached loader.
- Download the wordlist file from https://www.eff.org/files/2016/07/18/eff_large_wordlist.txt and commit it as-is.
