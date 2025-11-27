package io.maestro.core.executions.validation

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.maestro.model.errors.InvalidParameterValueException
import io.maestro.model.parameters.BooleanParameterType
import io.maestro.model.parameters.FloatParameterType
import io.maestro.model.parameters.IntegerParameterType
import io.maestro.model.parameters.StringParameterType
import org.junit.jupiter.api.Test

/**
 * Unit tests for ParameterTypeValidator.
 *
 * Tests cover:
 * - String→integer coercion
 * - String→float coercion
 * - String→boolean coercion
 * - Rejection of ambiguous coercions (floats for integers, integers for booleans)
 */
class ParameterTypeValidatorUnitTest {

    @Test
    fun `validateAndConvert should accept valid integer`() {
        // Given: Integer value
        val value = 42

        // When: Validating as integer
        val convertedValue = IntegerParameterType.validateAndConvert(value)

        // Then: Should be valid
        convertedValue shouldBe 42
    }

    @Test
    fun `validateAndConvert should coerce numeric string to integer`() {
        // Given: Numeric string
        val value = "123"

        // When: Validating as integer
        val convertedValue = IntegerParameterType.validateAndConvert(value)

        // Then: Should be valid and coerced
        convertedValue shouldBe 123
    }

    @Test
    fun `validateAndConvert should reject non-numeric string for integer`() {
        // Given: Non-numeric string
        val value = "not-a-number"

        // When/Then: Validating as integer should throw
        val exception = shouldThrow<InvalidParameterValueException> {
            IntegerParameterType.validateAndConvert(value)
        }
        exception.reason shouldContain "must be an integer"
    }

    @Test
    fun `validateAndConvert should reject float for integer`() {
        // Given: Float value
        val value = 3.14f

        // When/Then: Validating as integer should throw (precision loss)
        val exception = shouldThrow<InvalidParameterValueException> {
            IntegerParameterType.validateAndConvert(value)
        }
        exception.reason shouldContain "floats not allowed"
    }

    @Test
    fun `validateAndConvert should accept valid float`() {
        // Given: Float value
        val value = 3.14f

        // When: Validating as float
        val convertedValue = FloatParameterType.validateAndConvert(value)

        // Then: Should be valid
        convertedValue shouldBe 3.14f
    }

    @Test
    fun `validateAndConvert should coerce numeric string to float`() {
        // Given: Numeric string
        val value = "3.14"

        // When: Validating as float
        val convertedValue = FloatParameterType.validateAndConvert(value)

        // Then: Should be valid and coerced
        convertedValue shouldBe 3.14f
    }

    @Test
    fun `validateAndConvert should coerce integer to float`() {
        // Given: Integer value
        val value = 42

        // When: Validating as float
        val convertedValue = FloatParameterType.validateAndConvert(value)

        // Then: Should be valid and coerced
        convertedValue shouldBe 42.0f
    }

    @Test
    fun `validateAndConvert should reject non-numeric string for float`() {
        // Given: Non-numeric string
        val value = "not-a-number"

        // When/Then: Validating as float should throw
        val exception = shouldThrow<InvalidParameterValueException> {
            FloatParameterType.validateAndConvert(value)
        }
        exception.reason shouldContain "must be a float"
    }

    @Test
    fun `validateAndConvert should accept valid boolean`() {
        // Given: Boolean value
        val value = true

        // When: Validating as boolean
        val convertedValue = BooleanParameterType.validateAndConvert(value)

        // Then: Should be valid
        convertedValue shouldBe true
    }

    @Test
    fun `validateAndConvert should coerce true string to boolean`() {
        // Given: "true" string
        val value = "true"

        // When: Validating as boolean
        val convertedValue = BooleanParameterType.validateAndConvert(value)

        // Then: Should be valid and coerced
        convertedValue shouldBe true
    }

    @Test
    fun `validateAndConvert should coerce false string to boolean`() {
        // Given: "false" string
        val value = "false"

        // When: Validating as boolean
        val convertedValue = BooleanParameterType.validateAndConvert(value)

        // Then: Should be valid and coerced
        convertedValue shouldBe false
    }

    @Test
    fun `validateAndConvert should coerce case-insensitive boolean strings`() {
        // Given: Case-varied boolean strings
        val trueValues = listOf("TRUE", "True", "true", "  true  ")
        val falseValues = listOf("FALSE", "False", "false", "  false  ")

        // When/Then: All should be valid
        for (value in trueValues) {
            val convertedValue = BooleanParameterType.validateAndConvert(value)
            convertedValue shouldBe true
        }

        for (value in falseValues) {
            val convertedValue = BooleanParameterType.validateAndConvert(value)
            convertedValue shouldBe false
        }
    }

    @Test
    fun `validateAndConvert should reject invalid boolean string`() {
        // Given: Invalid boolean string
        val value = "maybe"

        // When/Then: Validating as boolean should throw
        val exception = shouldThrow<InvalidParameterValueException> {
            BooleanParameterType.validateAndConvert(value)
        }
        exception.reason shouldContain "must be a boolean"
    }

    @Test
    fun `validateAndConvert should reject integer for boolean`() {
        // Given: Integer value
        val value = 1

        // When/Then: Validating as boolean should throw (ambiguous)
        val exception = shouldThrow<InvalidParameterValueException> {
            BooleanParameterType.validateAndConvert(value)
        }
        exception.reason shouldContain "integers not allowed"
    }

    @Test
    fun `validateAndConvert should accept any value for string`() {
        // Given: Various value types
        val values = listOf("text", 42, 3.14f, true)

        // When/Then: All should be valid as strings
        for (value in values) {
            val convertedValue = StringParameterType.validateAndConvert(value)
            convertedValue shouldNotBe null
        }
    }

    @Test
    fun `validateAndConvert should trim string values`() {
        // Given: String with whitespace
        val value = "  123  "

        // When: Validating as integer
        val convertedValue = IntegerParameterType.validateAndConvert(value)

        // Then: Should be valid after trimming
        convertedValue shouldBe 123
    }

    @Test
    fun `validateAndConvert should reject null value`() {
        // Given: Null value
        val value: Any? = null

        // When/Then: Validating as any type should throw
        val exceptionInt = shouldThrow<InvalidParameterValueException> {
            IntegerParameterType.validateAndConvert(value)
        }
        exceptionInt.reason shouldContain "cannot be null"

        val exceptionFloat = shouldThrow<InvalidParameterValueException> {
            FloatParameterType.validateAndConvert(value)
        }
        exceptionFloat.reason shouldContain "cannot be null"

        val exceptionBoolean = shouldThrow<InvalidParameterValueException> {
            BooleanParameterType.validateAndConvert(value)
        }
        exceptionBoolean.reason shouldContain "cannot be null"
    }
}
