/**
 * Copyright 2023 Viacheslav Barkov
 */

package com.amias.texttoimagesearch.viewmodels

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import android.app.Application
import android.util.JsonReader
import androidx.lifecycle.AndroidViewModel
import com.amias.texttoimagesearch.R
import com.amias.texttoimagesearch.normalizeL2
import com.amias.texttoimagesearch.tokenizer.ClipTokenizer
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.IntBuffer
import java.util.HashMap

/**
 * ORTTextViewModel is an Android ViewModel responsible for managing text embeddings using the ONNX Runtime.
 *
 * This class handles the loading of a pre-trained text model, tokenization vocabulary, and merge rules.
 * It provides methods to initialize the model and generate text embeddings for given input strings.
 * It uses ONNX Runtime (ORT) for model inference and a custom tokenizer for text processing.
 *
 * @param application The application instance.
 */
class ORTTextViewModel(application: Application) : AndroidViewModel(application) {
    private val ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val modelID = R.raw.textual_quant
    private val resources = getApplication<Application>().resources
    private val model = resources.openRawResource(modelID).readBytes()
    private val session = ortEnv.createSession(model)

    private val tokenizerVocab: Map<String, Int> = getVocab()
    private val tokenizerMerges: HashMap<Pair<String, String>, Int> = getMerges()
    private val tokenBOS: Int = 49406
    private val tokenEOS: Int = 49407
    private val tokenizer = ClipTokenizer(tokenizerVocab, tokenizerMerges)

    private val queryFilter = Regex("[^A-Za-z0-9 ]")

    fun init() {
    }

    /**
     * Generates a text embedding for the given input text using a pre-trained language model.
     *
     * This function takes a string as input, preprocesses it, tokenizes it,
     * converts it into a numerical representation (tensor), and feeds it to
     * a pre-trained model to generate a fixed-size vector (embedding) representing
     * the semantic meaning of the text.
     *
     * The function performs the following steps:
     * 1. **Preprocessing:** Cleans the input text by removing unwanted characters using `queryFilter` and converting to lowercase.
     * 2. **Tokenization:** Encodes the cleaned text into a sequence of integer tokens using `tokenizer`.
     *    Special tokens [BOS] and [EOS] are added at the beginning and end, respectively.
     * 3. **Padding/Truncation:** Pads the token sequence with zeros or truncates it to a fixed length of 77.
     * 4. **Attention Mask Creation:** Generates an attention mask of the same length as the token sequence,
     *    with 1s for valid tokens and 0s for padding tokens.
     * 5. **Tensor Conversion:** Converts the token sequence and attention mask into OnnxTensors,
     *    which are the required input format for the ONNX Runtime.
     * 6. **Model Inference:** Runs the ONNX model with the input tensors to obtain the output.
     * 7. **Output Processing:** Extracts the embedding vector from the model's output and normalizes it using L2 normalization.
     *
     * @param text The input text string for which to generate the embedding.
     * @return A FloatArray representing the text embedding, normalized to unit length.
     * @throws Exception if there's an error during tensor creation or model execution.
     */
    fun getTextEmbedding(text: String): FloatArray {
        // Tokenize
        val textClean = queryFilter.replace(text, "").lowercase()
        var tokens: MutableList<Int> = ArrayList()
        tokens.add(tokenBOS)
        tokens.addAll(tokenizer.encode(textClean))
        tokens.add(tokenEOS)

        var mask: MutableList<Int> = ArrayList()
        for (i in 0 until tokens.size) {
            mask.add(1)
        }
        while (tokens.size < 77) {
            tokens.add(0)
            mask.add(0)
        }
        tokens = tokens.subList(0, 77)
        mask = mask.subList(0, 77)

        // Convert to tensor
        val inputShape = longArrayOf(1, 77)
        val inputIds = IntBuffer.allocate(1 * 77)
        inputIds.rewind()
        for (i in 0 until 77) {
            inputIds.put(tokens[i])
        }
        inputIds.rewind()
        val inputIdsTensor = OnnxTensor.createTensor(ortEnv, inputIds, inputShape)

        val attentionMask = IntBuffer.allocate(1 * 77)
        attentionMask.rewind()
        for (i in 0 until 77) {
            attentionMask.put(mask[i])
        }
        attentionMask.rewind()
        val attentionMaskTensor = OnnxTensor.createTensor(ortEnv, attentionMask, inputShape)

        val inputMap: MutableMap<String, OnnxTensor> = HashMap()
        inputMap["input_ids"] = inputIdsTensor
        inputMap["attention_mask"] = attentionMaskTensor

        val output = session?.run(inputMap)
        output.use {
            @Suppress("UNCHECKED_CAST") var rawOutput =
                ((output?.get(0)?.value) as Array<FloatArray>)[0]
            rawOutput = normalizeL2(rawOutput)
            return rawOutput
        }
    }

    /**
     * Retrieves the vocabulary from a JSON file stored as a raw resource.
     *
     * This function reads a JSON file named "vocab" located in the raw resources directory (R.raw.vocab).
     * The JSON file is expected to be an object where:
     *   - Keys are strings representing words (potentially with "</w>" suffix, which is replaced with a space).
     *   - Values are integers representing the word's ID or index in the vocabulary.
     *
     * The function parses this JSON and returns a Map where:
     *   - Keys are the words (with "</w>" replaced by spaces).
     *   - Values are their corresponding integer IDs.
     *
     * @return A Map<String, Int> representing the vocabulary, where keys are words and values are their IDs.
     * @throws java.io.IOException if there is an error reading the resource file.
     * @throws IllegalStateException if the JSON format is invalid.
     */
    fun getVocab(): Map<String, Int> {
        val vocab = hashMapOf<String, Int>().apply {
            resources.openRawResource(R.raw.vocab).use {
                val vocabReader = JsonReader(InputStreamReader(it, "UTF-8"))
                vocabReader.beginObject()
                while (vocabReader.hasNext()) {
                    val key = vocabReader.nextName().replace("</w>", " ")
                    val value = vocabReader.nextInt()
                    put(key, value)
                }
                vocabReader.close()
            }
        }
        return vocab
    }

    /**
     * Retrieves a map of merge pairs and their corresponding indices from a raw resource file.
     *
     * This function reads a file named "merges" from the raw resources directory (R.raw.merges).
     * Each line in the file represents a potential merge, consisting of two space-separated strings.
     * The function processes these lines, constructs a pair of strings (key) from each line,
     * and maps this pair to an integer index (value) representing the line number (starting from 0 after skipping the header).
     * The second string in each pair (representing a word or a part of a word) has "</w>" suffix replaced by a space
     *
     * The first line of the file is considered a header and is skipped.
     *
     * @return A HashMap where:
     *         - Key: A Pair<String, String> representing the merge pair. The second string is preprocessed replacing </w> suffix by a space.
     *         - Value: An Int representing the index of the merge pair in the file (starting from 0, excluding the header).
     *
     * @throws Resources.NotFoundException if the "merges" resource file is not found.
     * @throws java.io.IOException if an error occurs while reading the resource file.
     *
     * Example usage (assuming the file 'merges' in resources/raw is well-formed):
     *
     * ```kotlin
     * val mergeMap = getMerges()
     * println(mergeMap[Pair("a", "b ")]) //might print a value if there is a line "a b</w>" in merges file
     * ```
     *
     * Example of a merges file:
     * ```
     * header1 header2
     * a b</w>
     * c d</w>
     * e f</w>
     * ```
     */
    fun getMerges(): HashMap<Pair<String, String>, Int> {
        val merges = hashMapOf<Pair<String, String>, Int>().apply {
            resources.openRawResource(R.raw.merges).use {
                val mergesReader = BufferedReader(InputStreamReader(it))
                mergesReader.useLines { seq ->
                    seq.drop(1).forEachIndexed { i, s ->
                        val list = s.split(" ")
                        val keyTuple = list[0] to list[1].replace("</w>", " ")
                        put(keyTuple, i)
                    }
                }
            }
        }
        return merges
    }
}