/**
 * Copyright 2023 Viacheslav Barkov
 * Copyright 2020 The HuggingFace Team
 *
 * The following code is a derivative work of the code from the TensorFlow Lite Transformers
 * with Android project, which is licensed Apache 2.0
 */

package com.amias.texttoimagesearch.tokenizer

/**
 * `ClipTokenizer` is responsible for tokenizing text using a Byte Pair Encoding (BPE) model,
 * specifically designed for use with CLIP (Contrastive Language-Image Pre-training) models.
 *
 * This class provides functionality to encode text into a sequence of integers, which can then
 * be fed into a CLIP model for further processing. It leverages a pre-defined vocabulary
 * (`encoder`) and a set of BPE merge ranks (`bpeRanks`) to perform the tokenization.
 *
 * @property encoder A map where keys are tokens (strings) and values are their corresponding integer IDs.
 *                   This map represents the vocabulary of the tokenizer.
 * @property bpeRanks A map defining the BPE merge ranks. Keys are pairs of tokens (strings), and
 *                    values are integers representing the rank of that merge. Lower ranks indicate
 *                    higher priority merges.
 *
 * @constructor Creates a `ClipTokenizer` instance with the specified vocabulary and BPE merge ranks.
 *
 * Example usage:
 * ```
 * val encoder = mapOf("a" to 0, "b" to 1, "ab" to 2, "<|startoftext|>" to 3, "<|endoftext|>" to 4, " " to 5, "s" to 6)
 * val bpeRanks = mapOf(Pair("a", "b") to 0)
 * val byteEncoder = (0..255).associate { it to byteArrayOf(it.toByte()).decodeToString() }
 * val tokenizer = ClipTokenizer(encoder, bpeRanks)
 * val text = "ab"
 * val encoded = tokenizer.encode(text)
 * println(encoded) // Output will be determined based on the implementation and encoder.
 * ```
 */
class ClipTokenizer(
    private val encoder: Map<String, Int>,
    private val bpeRanks: Map<Pair<String, String>, Int>,
) {
    private val encodeRegex =
        Regex("""<\|startoftext\|>|<\|endoftext\|>|(?:'s|'t|'re|'ve|'m|'ll|'d)|\b[\p{L}]+\b|[\p{N}]+|[^\s\p{L}\p{N}]+""")

    /**
     * Encodes a given text string into a list of integers using a Byte Pair Encoding (BPE) model.
     *
     * This function performs the following steps:
     * 1. **Tokenization:** Splits the input text into tokens based on a predefined regular expression.
     *    - The `encodeRegex` (which should be defined elsewhere, e.g., `private val encodeRegex = Pattern.compile("'s|'t|'re|'ve|'m|'ll|'d| ?\\p{L}+| ?\\p{N}+| ?[^\\s\\p{L}\\p{N}]+|\\s+(?!\\S)|\\s+")`)
     *      is used to find all matches in the text. Each match represents a token.
     *    - This regex is designed to split text into words and special characters, while treating contractions and numbers separately.
     *
     * 2. **Byte Encoding:** Encodes each token into a sequence of bytes using a byte encoder mapping.
     *    -  `byteEncoder` (which should be defined elsewhere as a `Map<Int, String>`, e.g., `private val byteEncoder = ...`)
     *       is a dictionary that maps integer code points (representing characters) to string representations of their byte encoding.
     *    - The `codePoints()` method of the matched token is used to get the integer code points of each character.
     *    - `boxed()` wraps the `IntStream` into a `Stream<Int>`.
     *    - `map { byteEncoder[it]!! }` maps each code point to its byte encoding using the `byteEncoder` map.  `!!` is used for non-null assertion since we assume all code points are in the map.
     *    - `toArray()` converts the stream of encoded bytes to an array.
     *    - `joinToString("")` concatenates the encoded bytes into a single string.
     *
     * 3. **BPE Encoding:** Applies the Byte Pair Encoding (BPE) algorithm to each encoded token.
     *    - `bpe(it)` (which should be defined elsewhere, e.g., `private fun bpe(token: String): List<String> {...}`)
     */
    fun encode(text: String): MutableList<Int> {
        if (text.isBlank()) return mutableListOf() // Handle empty input

        val tokens =
            encodeRegex.findAll(text).map { result ->
                result.value
                    .codePoints()
                    .boxed()
                    .map { byteEncoder[it]!! }
                    .toArray()
                    .joinToString("")
            }
        return tokens
            .flatMap {
                val bpeResult = bpe(it)
                bpeResult.mapNotNull { subToken -> encoder[subToken] }
            }.toMutableList()
    }

    /**
     * Applies Byte Pair Encoding (BPE) to a single token.
     *
     * This function takes a token (string) and breaks it down into subword units based on
     * the BPE algorithm and a pre-defined ranking of pairs (`bpeRanks`). It iteratively merges
     * the most frequent adjacent character/subword pairs in the token until no more pairs
     * exist in the `bpeRanks` or the token is reduced to a single unit.
     *
     * @param token The input token to be processed by BPE.
     * @return A list of strings representing the subword units of the token after BPE is applied.
     *         Each subword unit is a string, and the last one ends with a space to denote the end of the token.
     *         If the token has a length of 0 or 1, returns the token with a trailing space as a single-element list.
     *
     * @throws IllegalArgumentException if the input token is null.
     *
     * Example:
     *
     * Suppose `bpeRanks` contains:
     *   ("es", 1)
     *   ("st", 2)
     *   ("ste", 3)
     *
     * And the input is "testes".
     * 1. Initially, the word is ["t", "e", "s", "t", "e", "s "].
     * 2. The most frequent pair in bpeRanks is "es" (rank 1). The word becomes ["t", "e", "s", "t", "es "].
     * 3. The next most frequent pair is "st" (rank 2). But "st" not exists.
     * 4. The next most frequent pair is "ste" (rank 3). The word becomes ["t", "es", "t","es "] . but there is not "ste".
     * 5. No more pairs exists.
     * 6. result : ["t", "es", "t","es "]
     *
     */
    private fun bpe(token: String): List<String> {
        if (token.length <= 1) return listOf("$token ")

        val wordWithBreak = token.map { it.toString() }.toMutableList()
        wordWithBreak[wordWithBreak.size - 1] = "${wordWithBreak[wordWithBreak.size - 1]} "
        var word = wordWithBreak.toList()
        var pairs = getPairs(word)

        // Track already processed pairs to avoid redundant work
        val processedPairs = mutableSetOf<Pair<String, String>>()

        while (true) {
            // Filter out already processed pairs
            val validPairs = pairs.filter { !processedPairs.contains(it) && bpeRanks.containsKey(it) }
            if (validPairs.isEmpty()) break

            val bigrams = validPairs.associateWith { bpeRanks.getOrDefault(it, Int.MAX_VALUE) }
            val (first, second) = bigrams.minByOrNull { it.value }?.key ?: break

            // Mark this pair as processed
            processedPairs.add(first to second)

            var i = 0
            val newWord = mutableListOf<String>()

            while (i < word.size) {
                // Find next occurrence of first token starting from position i
                val j = word.withIndex().indexOfFirst { it.index >= i && it.value == first }

                if (j == -1) {
                    // No more occurrences, add remaining tokens
                    newWord.addAll(word.subList(i, word.size))
                    break
                } else {
                    // Add tokens before the occurrence
                    newWord.addAll(word.subList(i, j))
                    i = j
                }

                // Check if we can merge
                if (i < word.size - 1 && word[i] == first && word[i + 1] == second) {
                    newWord.add(first + second)
                    i += 2
                } else {
                    newWord.add(word[i])
                    i += 1
                }
            }

            word = newWord
            if (word.size == 1) {
                break
            } else {
                pairs = getPairs(word)
            }
        }

        return word
    }

    /**
     * Generates a set of consecutive pairs of strings from a given list of strings.
     *
     * This function iterates through the input list and creates pairs consisting of each
     * element and the element that immediately follows it. The resulting pairs are stored
     * in a set, ensuring that only unique pairs are included.
     *
     * For example, if the input list is ["apple", "banana", "cherry"], the output will be:
     * {("apple", "banana"), ("banana", "cherry")}
     *
     * Note that if the input list contains fewer than two elements, an empty set will be returned
     * because no pairs can be formed.
     *
     * @param word The input list of strings.
     * @return A set containing unique pairs of consecutive strings from the input list.
     */
    private fun getPairs(word: List<String>): Set<Pair<String, String>> =
        mutableSetOf<Pair<String, String>>().apply {
            for (i in 0 until word.size - 1) {
                add(word[i] to word[i + 1])
            }
        }
}
