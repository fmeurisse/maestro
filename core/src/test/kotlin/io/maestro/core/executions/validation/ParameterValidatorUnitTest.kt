package io.maestro.core.executions.validation

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.maestro.core.parameters.ParameterTypeRegistry
import io.maestro.core.parameters.ParameterValidator
import io.maestro.model.WorkflowRevisionID
import io.maestro.model.parameters.BooleanParameterType
import io.maestro.model.parameters.FloatParameterType
import io.maestro.model.parameters.IntegerParameterType
import io.maestro.model.parameters.ParameterDefinition
import io.maestro.model.parameters.StringParameterType
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

/**
 * Unit tests for ParameterValidator.
 *
 * Tests cover:
 * - Type validation (string, integer, float, boolean)
 * - Required field validation
 * - Extra fields rejection
 * - Type coercion rules
 * - Default value application
 */
class ParameterValidatorUnitTest {

    private val registry = mockk<ParameterTypeRegistry>().apply {
        every { getType("STRING") } returns StringParameterType
        every { getType("INTEGER") } returns IntegerParameterType
        every { getType("FLOAT") } returns FloatParameterType
        every { getType("BOOLEAN") } returns BooleanParameterType
    }

    private val validator = ParameterValidator()
    private val revisionId = WorkflowRevisionID("test-ns", "test-workflow", 1)

    @Test
    fun `validate should accept valid parameters matching schema`() {
        // Given: Schema with string and integer parameters
        val schema = listOf(
            ParameterDefinition("userName", StringParameterType, required = true),
            ParameterDefinition("retryCount", IntegerParameterType, required = true)
        )
        val parameters = mapOf(
            "userName" to "alice",
            "retryCount" to 3
        )

        // When: Validating parameters
        val result = validator.validate(parameters, schema, revisionId)

        // Then: Validation should pass
        result.isValid shouldBe true
        result.errors.isEmpty() shouldBe true
    }

    @Test
    fun `validate should reject missing required parameters`() {
        // Given: Schema with required parameter
        val schema = listOf(
            ParameterDefinition("userName", StringParameterType, required = true)
        )
        val parameters = emptyMap<String, Any>()

        // When: Validating parameters
        val result = validator.validate(parameters, schema, revisionId)

        // Then: Validation should fail with missing parameter error
        result.isValid shouldBe false
        result.errors.size shouldBe 1
        result.errors[0].name shouldBe "userName"
        result.errors[0].reason shouldBe "required parameter missing"
    }

    @Test
    fun `validate should reject extra parameters not in schema`() {
        // Given: Schema with one parameter
        val schema = listOf(
            ParameterDefinition("userName", StringParameterType, required = true)
        )
        val parameters = mapOf(
            "userName" to "alice",
            "extraParam" to "value"
        )

        // When: Validating parameters
        val result = validator.validate(parameters, schema, revisionId)

        // Then: Validation should fail with extra parameter error
        result.isValid shouldBe false
        result.errors.size shouldBe 1
        result.errors[0].name shouldBe "extraParam"
        result.errors[0].reason shouldBe "parameter not defined in schema"
    }

    @Test
    fun `validate should apply default values for missing optional parameters`() {
        // Given: Schema with optional parameter with default
        val schema = listOf(
            ParameterDefinition("userName", StringParameterType, required = true),
            ParameterDefinition("retryCount", IntegerParameterType, required = false, default = 3)
        )
        val parameters = mapOf(
            "userName" to "alice"
        )

        // When: Validating parameters
        val result = validator.validate(parameters, schema, revisionId)

        // Then: Validation should pass and default should be applied
        result.isValid shouldBe true
        result.errors.size shouldBe 0
        result.validatedParameters["retryCount"] shouldBe 3
    }

    @Test
    fun `validate should reject type mismatch for integer`() {
        // Given: Schema expecting integer
        val schema = listOf(
            ParameterDefinition("retryCount", IntegerParameterType, required = true)
        )
        val parameters = mapOf(
            "retryCount" to "not-a-number"
        )

        // When: Validating parameters
        val result = validator.validate(parameters, schema, revisionId)

        // Then: Validation should fail with type error
        result.isValid shouldBe false
        result.errors.size shouldBe 1
        result.errors[0].name shouldBe "retryCount"
        result.errors[0].reason shouldContain "must be an integer"
    }

    @Test
    fun `validate should reject type mismatch for float`() {
        // Given: Schema expecting float
        val schema = listOf(
            ParameterDefinition("price", FloatParameterType, required = true)
        )
        val parameters = mapOf(
            "price" to "not-a-number"
        )

        // When: Validating parameters
        val result = validator.validate(parameters, schema, revisionId)

        // Then: Validation should fail with type error
        result.isValid shouldBe false
        result.errors.size shouldBe 1
        result.errors[0].name shouldBe "price"
        result.errors[0].reason shouldContain "must be a float"
    }

    @Test
    fun `validate should reject type mismatch for boolean`() {
        // Given: Schema expecting boolean
        val schema = listOf(
            ParameterDefinition("enableDebug", BooleanParameterType, required = true)
        )
        val parameters = mapOf(
            "enableDebug" to "maybe"
        )

        // When: Validating parameters
        val result = validator.validate(parameters, schema, revisionId)

        // Then: Validation should fail with type error
        result.isValid shouldBe false
        result.errors.size shouldBe 1
        result.errors[0].name shouldBe "enableDebug"
        result.errors[0].reason shouldContain "must be a boolean"
    }

    @Test
    fun `validate should collect all validation errors`() {
        // Given: Multiple validation errors
        val schema = listOf(
            ParameterDefinition("userName", StringParameterType, required = true),
            ParameterDefinition("retryCount", IntegerParameterType, required = true)
        )
        val parameters = mapOf(
            "retryCount" to "invalid",
            "extraParam" to "value"
        )

        // When: Validating parameters
        val result = validator.validate(parameters, schema, revisionId)

        // Then: All errors should be collected
        result.isValid shouldBe false
        result.errors.size shouldBe 3
        result.errors.any { it.name == "userName" && it.reason == "required parameter missing" } shouldBe true
        result.errors.any { it.name == "retryCount" && it.reason.contains("must be an integer") } shouldBe true
        result.errors.any { it.name == "extraParam" && it.reason == "parameter not defined in schema" } shouldBe true
    }

    @Test
    fun `validate should accept empty parameters for schema with no required fields`() {
        // Given: Schema with only optional parameters
        val schema = listOf(
            ParameterDefinition("optionalParam", StringParameterType, required = false, default = "default")
        )
        val parameters = emptyMap<String, Any>()

        // When: Validating parameters
        val result = validator.validate(parameters, schema, revisionId)

        // Then: Validation should pass
        result.isValid shouldBe true
        result.errors.size shouldBe 0
    }

    @Test
    fun `validate should accept empty schema`() {
        // Given: Empty schema
        val schema = emptyList<ParameterDefinition>()
        val parameters = emptyMap<String, Any>()

        // When: Validating parameters
        val result = validator.validate(parameters, schema, revisionId)

        // Then: Validation should pass
        result.isValid shouldBe true
        result.errors.size shouldBe 0
    }
}
