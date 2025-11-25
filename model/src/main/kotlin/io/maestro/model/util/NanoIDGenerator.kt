package io.maestro.model.util

import java.security.SecureRandom

/**
 * Default alphabet: URL-safe characters (A-Za-z0-9_-)
 * This is the same alphabet used by the JavaScript implementation
 */
const val DEFAULT_ALPHABET = "_-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"

/**
 * Default size: 21 characters
 * Provides ~128 bits of entropy (similar to UUID v4)
 */
const val DEFAULT_SIZE = 21

/**
 * NanoID generator class.
 * 
 * NanoID is a tiny, URL-safe, unique string ID generator.
 * It's similar to UUID but shorter and URL-safe.
 * 
 * This class allows creating multiple generator instances with the same parameters
 * (alphabet, size, random) for consistent ID generation.
 * 
 * Based on: https://github.com/ai/nanoid
 * 
 * @param alphabet The alphabet to use for ID generation (default: DEFAULT_ALPHABET)
 * @param size The length of the generated ID (default: DEFAULT_SIZE)
 * @param random The random number generator to use (default: SecureRandom())
 */
class NanoIDGenerator(
    val alphabet: String = DEFAULT_ALPHABET,
    val size: Int = DEFAULT_SIZE,
    val random: java.util.Random = SecureRandom()
) {
    init {
        require(alphabet.isNotEmpty()) { "Alphabet must not be empty" }
        require(alphabet.length <= 256) { "Alphabet size must be <= 256, got: ${alphabet.length}" }
        require(size > 0) { "Size must be positive, got: $size" }
    }
    
    private val alphabetArray = alphabet.toCharArray()
    
    /**
     * Generates a new NanoID using this generator's configured alphabet and size.
     * 
     * @return A new NanoID string
     */
    fun generate(): String {
        val id = CharArray(size)
        
        for (i in 0 until size) {
            val randomIndex = random.nextInt(alphabetArray.size)
            id[i] = alphabetArray[randomIndex]
        }
        
        return String(id)
    }
    
    /**
     * Validates if a string is a valid NanoID format for this generator's alphabet.
     * 
     * @param id The string to validate
     * @param minSize Minimum size (default: 1)
     * @param maxSize Maximum size (default: 100)
     * @return true if the string is a valid NanoID format
     */
    fun isValid(
        id: String,
        minSize: Int = 1,
        maxSize: Int = 100
    ): Boolean {
        if (id.length < minSize || id.length > maxSize) {
            return false
        }
        
        val alphabetSet = alphabet.toSet()
        return id.all { it in alphabetSet }
    }
}

/**
 * Convenience object for generating NanoIDs with default parameters.
 * 
 * This object provides a simple API for common use cases. For more control,
 * create a [NanoIDGenerator] instance with custom parameters.
 */
object NanoID {
    
    /**
     * Default generator instance with default parameters
     */
    private val defaultGenerator = NanoIDGenerator()
    
    /**
     * Generates a new NanoID using the default alphabet and size.
     * 
     * @return A new NanoID string (21 characters, URL-safe)
     */
    fun generate(): String {
        return defaultGenerator.generate()
    }
    
    /**
     * Generates a new NanoID with a custom size.
     * 
     * @param size The length of the generated ID (default: 21)
     * @return A new NanoID string
     */
    fun generate(size: Int): String {
        require(size > 0) { "Size must be positive, got: $size" }
        return NanoIDGenerator(size = size).generate()
    }
    
    /**
     * Generates a new NanoID with a custom alphabet and size.
     * 
     * @param alphabet The alphabet to use for ID generation
     * @param size The length of the generated ID
     * @return A new NanoID string
     */
    fun generate(alphabet: String, size: Int): String {
        return NanoIDGenerator(alphabet = alphabet, size = size).generate()
    }
    
    /**
     * Validates if a string is a valid NanoID format.
     * 
     * @param id The string to validate
     * @param alphabet The expected alphabet (default: DEFAULT_ALPHABET)
     * @param minSize Minimum size (default: 1)
     * @param maxSize Maximum size (default: 100)
     * @return true if the string is a valid NanoID format
     */
    fun isValid(
        id: String,
        alphabet: String = DEFAULT_ALPHABET,
        minSize: Int = 1,
        maxSize: Int = 100
    ): Boolean {
        return NanoIDGenerator(alphabet = alphabet).isValid(id, minSize, maxSize)
    }
}
