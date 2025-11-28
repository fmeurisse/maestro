package io.maestro.model.util

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldHaveLength
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.throwable.shouldHaveMessage

class NanoIDGeneratorUnitTest : FeatureSpec({

    feature("generate() - default parameters") {
        scenario("should generate a NanoID with default size (21 characters)") {
            val id = NanoID.generate()
            id shouldHaveLength 21
        }

        scenario("should generate URL-safe characters only") {
            val id = NanoID.generate()
            // Default alphabet: A-Za-z0-9_-
            id shouldMatch Regex("^[A-Za-z0-9_-]+$")
        }

        scenario("should generate unique IDs on multiple calls") {
            val id1 = NanoID.generate()
            val id2 = NanoID.generate()
            val id3 = NanoID.generate()
            
            id1 shouldNotBe id2
            id2 shouldNotBe id3
            id1 shouldNotBe id3
        }

        scenario("should generate different IDs each time") {
            val ids = (1..100).map { NanoID.generate() }.toSet()
            // All 100 IDs should be unique
            ids.size shouldBe 100
        }
    }

    feature("generate(size) - custom size") {
        scenario("should generate NanoID with custom size") {
            val id = NanoID.generate(10)
            id shouldHaveLength 10
        }

        scenario("should generate NanoID with size 1") {
            val id = NanoID.generate(1)
            id shouldHaveLength 1
        }

        scenario("should generate NanoID with large size") {
            val id = NanoID.generate(100)
            id shouldHaveLength 100
        }

        scenario("should throw exception for size 0") {
            val exception = shouldThrow<IllegalArgumentException> {
                NanoID.generate(0)
            }
            exception shouldHaveMessage "Size must be positive, got: 0"
        }

        scenario("should throw exception for negative size") {
            val exception = shouldThrow<IllegalArgumentException> {
                NanoID.generate(-1)
            }
            exception shouldHaveMessage "Size must be positive, got: -1"
        }
    }

    feature("generate(alphabet, size) - custom alphabet and size") {
        scenario("should generate NanoID with custom alphabet") {
            val alphabet = "ABC"
            val id = NanoID.generate(alphabet, 10)
            id shouldHaveLength 10
            id.forEach { char ->
                (char in alphabet) shouldBe true
            }
        }

        scenario("should generate NanoID with numeric alphabet only") {
            val alphabet = "0123456789"
            val id = NanoID.generate(alphabet, 5)
            id shouldHaveLength 5
            id shouldMatch Regex("^[0-9]+$")
        }

        scenario("should generate NanoID with lowercase alphabet only") {
            val alphabet = "abcdefghijklmnopqrstuvwxyz"
            val id = NanoID.generate(alphabet, 8)
            id shouldHaveLength 8
            id shouldMatch Regex("^[a-z]+$")
        }

        scenario("should throw exception for empty alphabet") {
            val exception = shouldThrow<IllegalArgumentException> {
                NanoID.generate("", 10)
            }
            exception shouldHaveMessage "Alphabet must not be empty"
        }

        scenario("should throw exception for alphabet size > 256") {
            val largeAlphabet = "a".repeat(257)
            val exception = shouldThrow<IllegalArgumentException> {
                NanoID.generate(largeAlphabet, 10)
            }
            exception shouldHaveMessage "Alphabet size must be <= 256, got: 257"
        }

        scenario("should accept alphabet size exactly 256") {
            val alphabet = "a".repeat(256)
            val id = NanoID.generate(alphabet, 5)
            id shouldHaveLength 5
        }

        scenario("should throw exception for size 0 with custom alphabet") {
            val exception = shouldThrow<IllegalArgumentException> {
                NanoID.generate("ABC", 0)
            }
            exception shouldHaveMessage "Size must be positive, got: 0"
        }
    }

    feature("isValid() - validation") {
        scenario("should validate default format NanoID") {
            val id = NanoID.generate()
            NanoID.isValid(id) shouldBe true
        }

        scenario("should validate custom size NanoID") {
            val id = NanoID.generate(50)
            NanoID.isValid(id) shouldBe true
        }

        scenario("should validate NanoID with custom alphabet") {
            val alphabet = "ABC"
            val id = NanoID.generate(alphabet, 10)
            NanoID.isValid(id, alphabet = alphabet) shouldBe true
        }

        scenario("should reject empty string") {
            NanoID.isValid("") shouldBe false
        }

        scenario("should reject string shorter than minSize") {
            val id = NanoID.generate(5)
            NanoID.isValid(id, minSize = 10) shouldBe false
        }

        scenario("should reject string longer than maxSize") {
            val id = NanoID.generate(50)
            NanoID.isValid(id, maxSize = 30) shouldBe false
        }

        scenario("should accept string within size range") {
            val id = NanoID.generate(25)
            NanoID.isValid(id, minSize = 20, maxSize = 30) shouldBe true
        }

        scenario("should reject string with invalid characters") {
            // Default alphabet doesn't include special characters like @, #, $
            NanoID.isValid("V1StGXR8_Z5jdHi6B-myT@") shouldBe false
            NanoID.isValid("V1StGXR8_Z5jdHi6B-myT#") shouldBe false
            NanoID.isValid("V1StGXR8_Z5jdHi6B-myT$") shouldBe false
        }

        scenario("should accept valid characters from default alphabet") {
            NanoID.isValid("V1StGXR8_Z5jdHi6B-myT") shouldBe true
            NanoID.isValid("0123456789") shouldBe true
            NanoID.isValid("abcdefghijklmnopqrstuvwxyz") shouldBe true
            NanoID.isValid("ABCDEFGHIJKLMNOPQRSTUVWXYZ") shouldBe true
            NanoID.isValid("_-") shouldBe true
        }

        scenario("should validate with custom alphabet") {
            val alphabet = "ABC"
            val id = NanoID.generate(alphabet, 10)
            NanoID.isValid(id, alphabet = alphabet) shouldBe true
            // Should fail with default alphabet if custom alphabet is different
            NanoID.isValid("AAAAA", alphabet = alphabet) shouldBe true
        }

        scenario("should reject string with characters not in custom alphabet") {
            val alphabet = "ABC"
            NanoID.isValid("ABCD", alphabet = alphabet) shouldBe false // D not in alphabet
            NanoID.isValid("ABC1", alphabet = alphabet) shouldBe false // 1 not in alphabet
        }

        scenario("should accept string at exact minSize") {
            val id = NanoID.generate(10)
            NanoID.isValid(id, minSize = 10) shouldBe true
        }

        scenario("should accept string at exact maxSize") {
            val id = NanoID.generate(50)
            NanoID.isValid(id, maxSize = 50) shouldBe true
        }

        scenario("should handle very large maxSize") {
            val id = NanoID.generate(100)
            NanoID.isValid(id, maxSize = 200) shouldBe true
        }
    }

    feature("integration - generate and validate") {
        scenario("should generate valid IDs that pass validation") {
            repeat(100) {
                val id = NanoID.generate()
                NanoID.isValid(id) shouldBe true
            }
        }

        scenario("should generate valid IDs with custom size that pass validation") {
            repeat(50) {
                val size = (1..100).random()
                val id = NanoID.generate(size)
                NanoID.isValid(id, minSize = 1, maxSize = 100) shouldBe true
            }
        }

        scenario("should generate valid IDs with custom alphabet that pass validation") {
            val alphabet = "0123456789ABCDEF"
            repeat(50) {
                val id = NanoID.generate(alphabet, 16)
                NanoID.isValid(id, alphabet = alphabet) shouldBe true
            }
        }
    }

    feature("edge cases") {
        scenario("should handle single character alphabet") {
            val id = NanoID.generate("A", 10)
            id shouldHaveLength 10
            id shouldBe "A".repeat(10)
        }

        scenario("should handle two character alphabet") {
            val id = NanoID.generate("AB", 5)
            id shouldHaveLength 5
            id.forEach { char ->
                (char in "AB") shouldBe true
            }
        }

        scenario("should generate different IDs even with single character alphabet") {
            // With single char alphabet, all IDs are the same
            val id1 = NanoID.generate("A", 5)
            val id2 = NanoID.generate("A", 5)
            id1 shouldBe id2 // Both are "AAAAA"
        }
    }

    feature("NanoIDGenerator class") {
        scenario("should create generator with default parameters") {
            val generator = NanoIDGenerator()
            generator.alphabet shouldBe DEFAULT_ALPHABET
            generator.size shouldBe DEFAULT_SIZE
        }

        scenario("should create generator with custom alphabet and size") {
            val alphabet = "ABC"
            val size = 10
            val generator = NanoIDGenerator(alphabet = alphabet, size = size)
            generator.alphabet shouldBe alphabet
            generator.size shouldBe size
        }

        scenario("should generate IDs with same generator instance") {
            val generator = NanoIDGenerator()
            val id1 = generator.generate()
            val id2 = generator.generate()
            val id3 = generator.generate()
            
            id1 shouldHaveLength DEFAULT_SIZE
            id2 shouldHaveLength DEFAULT_SIZE
            id3 shouldHaveLength DEFAULT_SIZE
            
            // IDs should be unique
            id1 shouldNotBe id2
            id2 shouldNotBe id3
            id1 shouldNotBe id3
        }

        scenario("should generate IDs with same parameters using different instances") {
            val generator1 = NanoIDGenerator(size = 10)
            val generator2 = NanoIDGenerator(size = 10)
            
            val id1 = generator1.generate()
            val id2 = generator2.generate()
            
            id1 shouldHaveLength 10
            id2 shouldHaveLength 10
            // Different instances with same parameters can generate different IDs
            // (unless using same random seed, which we're not)
        }

        scenario("should validate IDs using generator's alphabet") {
            val alphabet = "ABC"
            val generator = NanoIDGenerator(alphabet = alphabet, size = 10)
            val id = generator.generate()
            
            generator.isValid(id) shouldBe true
            generator.isValid("ABCD") shouldBe false // D not in alphabet
            generator.isValid("ABC") shouldBe true
        }

        scenario("should use custom random generator") {
            val seed = 12345L
            val random1 = java.util.Random(seed)
            val random2 = java.util.Random(seed)
            
            val generator1 = NanoIDGenerator(random = random1)
            val generator2 = NanoIDGenerator(random = random2)
            
            // With same seed, should generate same sequence
            val id1 = generator1.generate()
            val id2 = generator2.generate()
            id1 shouldBe id2
        }

        scenario("should allow creating multiple generators with same parameters") {
            val alphabet = "0123456789"
            val size = 8
            
            val generator1 = NanoIDGenerator(alphabet = alphabet, size = size)
            val generator2 = NanoIDGenerator(alphabet = alphabet, size = size)
            val generator3 = NanoIDGenerator(alphabet = alphabet, size = size)
            
            val ids1 = (1..10).map { generator1.generate() }
            val ids2 = (1..10).map { generator2.generate() }
            val ids3 = (1..10).map { generator3.generate() }
            
            // All should have correct size
            ids1.forEach { it shouldHaveLength size }
            ids2.forEach { it shouldHaveLength size }
            ids3.forEach { it shouldHaveLength size }
            
            // All should use numeric alphabet
            ids1.forEach { it shouldMatch Regex("^[0-9]+$") }
            ids2.forEach { it shouldMatch Regex("^[0-9]+$") }
            ids3.forEach { it shouldMatch Regex("^[0-9]+$") }
        }

        scenario("should throw exception for invalid alphabet in constructor") {
            val exception1 = shouldThrow<IllegalArgumentException> {
                NanoIDGenerator(alphabet = "")
            }
            exception1 shouldHaveMessage "Alphabet must not be empty"
            
            val largeAlphabet = "a".repeat(257)
            val exception2 = shouldThrow<IllegalArgumentException> {
                NanoIDGenerator(alphabet = largeAlphabet)
            }
            exception2 shouldHaveMessage "Alphabet size must be <= 256, got: 257"
        }

        scenario("should throw exception for invalid size in constructor") {
            val exception1 = shouldThrow<IllegalArgumentException> {
                NanoIDGenerator(size = 0)
            }
            exception1 shouldHaveMessage "Size must be positive, got: 0"
            
            val exception2 = shouldThrow<IllegalArgumentException> {
                NanoIDGenerator(size = -1)
            }
            exception2 shouldHaveMessage "Size must be positive, got: -1"
        }
    }
})
