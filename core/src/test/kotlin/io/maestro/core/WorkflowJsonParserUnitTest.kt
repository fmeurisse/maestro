package io.maestro.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.maestro.core.errors.WorkflowRevisionParsingException
import io.maestro.core.steps.If
import io.maestro.core.steps.LogTask
import io.maestro.core.steps.Sequence
import io.maestro.model.errors.InvalidWorkflowRevisionException
import io.maestro.model.WorkflowRevision
import io.maestro.model.WorkflowRevisionID
import java.time.Instant

/**
 * Unit tests for WorkflowJsonParser.
 *
 * Tests verify:
 * - Parsing valid JSON workflow definitions
 * - Parsing various step types (LogTask, Sequence, If)
 * - Handling malformed JSON syntax
 * - Handling invalid workflow data (validation failures)
 * - Serialization to JSON (round-trip)
 * - Validation control (enabled/disabled)
 */
class WorkflowJsonParserUnitTest : FeatureSpec({

    val parser = WorkflowJsonParser()

    feature("parseRevision - valid JSON") {
        scenario("should parse simple workflow with LogTask") {
            // Given
            val json = """
                {
                    "namespace": "test-ns",
                    "id": "workflow-1",
                    "version": 1,
                    "name": "Test Workflow",
                    "description": "A simple test workflow",
                    "steps": [
                        {
                            "type": "LogTask",
                            "message": "Hello, World!"
                        }
                    ],
                    "active": false,
                    "createdAt": "2024-01-01T00:00:00Z",
                    "updatedAt": "2024-01-01T00:00:00Z"
                }
            """.trimIndent()

            // When
            val revision = parser.parseRevision(json)

            // Then
            revision.namespace shouldBe "test-ns"
            revision.id shouldBe "workflow-1"
            revision.version shouldBe 1
            revision.name shouldBe "Test Workflow"
            revision.description shouldBe "A simple test workflow"
            revision.active shouldBe false
            revision.steps.size shouldBe 1
            revision.steps[0] shouldBe LogTask("Hello, World!")
        }

        scenario("should parse workflow with Sequence") {
            // Given
            val json = """
                {
                    "namespace": "test-ns",
                    "id": "workflow-1",
                    "version": 1,
                    "name": "Sequence Workflow",
                    "description": "A workflow with sequence",
                    "steps": [
                        {
                            "type": "Sequence",
                            "steps": [
                                {
                                    "type": "LogTask",
                                    "message": "First task"
                                },
                                {
                                    "type": "LogTask",
                                    "message": "Second task"
                                }
                            ]
                        }
                    ],
                    "active": true,
                    "createdAt": "2024-01-01T00:00:00Z",
                    "updatedAt": "2024-01-01T00:00:00Z"
                }
            """.trimIndent()

            // When
            val revision = parser.parseRevision(json)

            // Then
            revision.steps.size shouldBe 1
            val sequence = revision.steps[0] as Sequence
            sequence.steps.size shouldBe 2
            sequence.steps[0] shouldBe LogTask("First task")
            sequence.steps[1] shouldBe LogTask("Second task")
        }

        scenario("should parse workflow with If step") {
            // Given
            val json = """
                {
                    "namespace": "test-ns",
                    "id": "workflow-1",
                    "version": 1,
                    "name": "Conditional Workflow",
                    "description": "A workflow with conditional logic",
                    "steps": [
                        {
                            "type": "If",
                            "condition": "${'$'}{env.ENABLE_FEATURE}",
                            "ifTrue": {
                                "type": "LogTask",
                                "message": "Feature enabled"
                            },
                            "ifFalse": {
                                "type": "LogTask",
                                "message": "Feature disabled"
                            }
                        }
                    ],
                    "active": false,
                    "createdAt": "2024-01-01T00:00:00Z",
                    "updatedAt": "2024-01-01T00:00:00Z"
                }
            """.trimIndent()

            // When
            val revision = parser.parseRevision(json)

            // Then
            revision.steps.size shouldBe 1
            val ifStep = revision.steps[0] as If
            ifStep.condition shouldBe "\${env.ENABLE_FEATURE}"
            ifStep.ifTrue shouldBe LogTask("Feature enabled")
            ifStep.ifFalse.shouldNotBeNull()
            ifStep.ifFalse shouldBe LogTask("Feature disabled")
        }

        scenario("should parse workflow with nested Sequence") {
            // Given
            val json = """
                {
                    "namespace": "test-ns",
                    "id": "workflow-1",
                    "version": 1,
                    "name": "Nested Sequence Workflow",
                    "description": "A workflow with nested sequences",
                    "steps": [
                        {
                            "type": "Sequence",
                            "steps": [
                                {
                                    "type": "LogTask",
                                    "message": "Before nested"
                                },
                                {
                                    "type": "Sequence",
                                    "steps": [
                                        {
                                            "type": "LogTask",
                                            "message": "Nested task 1"
                                        },
                                        {
                                            "type": "LogTask",
                                            "message": "Nested task 2"
                                        }
                                    ]
                                },
                                {
                                    "type": "LogTask",
                                    "message": "After nested"
                                }
                            ]
                        }
                    ],
                    "active": false,
                    "createdAt": "2024-01-01T00:00:00Z",
                    "updatedAt": "2024-01-01T00:00:00Z"
                }
            """.trimIndent()

            // When
            val revision = parser.parseRevision(json)

            // Then
            revision.steps.size shouldBe 1
            val outerSequence = revision.steps[0] as Sequence
            outerSequence.steps.size shouldBe 3
            outerSequence.steps[0] shouldBe LogTask("Before nested")
            outerSequence.steps[2] shouldBe LogTask("After nested")
            
            val innerSequence = outerSequence.steps[1] as Sequence
            innerSequence.steps.size shouldBe 2
            innerSequence.steps[0] shouldBe LogTask("Nested task 1")
            innerSequence.steps[1] shouldBe LogTask("Nested task 2")
        }

        scenario("should parse workflow with default values") {
            // Given - JSON without active (should use defaults)
            val json = """
                {
                    "namespace": "test-ns",
                    "id": "workflow-1",
                    "version": 1,
                    "name": "Default Values Workflow",
                    "description": "Testing defaults",
                    "steps": [
                        {
                            "type": "LogTask",
                            "message": "Test"
                        }
                    ],
                    "createdAt": "2024-01-01T00:00:00Z",
                    "updatedAt": "2024-01-01T00:00:00Z"
                }
            """.trimIndent()

            // When
            val revision = parser.parseRevision(json)

            // Then
            revision.active shouldBe false // Default value
        }
    }

    feature("parseRevision - validation") {
        scenario("should validate workflow data by default") {
            // Given - JSON with invalid namespace (blank)
            val json = """
                {
                    "namespace": "",
                    "id": "workflow-1",
                    "version": 1,
                    "name": "Invalid Workflow",
                    "description": "Should fail validation",
                    "steps": [
                        {
                            "type": "LogTask",
                            "message": "Test"
                        }
                    ],
                    "createdAt": "2024-01-01T00:00:00Z",
                    "updatedAt": "2024-01-01T00:00:00Z"
                }
            """.trimIndent()

            // When/Then
            val exception = shouldThrow<WorkflowRevisionParsingException> {
                parser.parseRevision(json)
            }
            exception.cause.shouldNotBeNull()
            (exception.cause is InvalidWorkflowRevisionException) shouldBe true
        }

        scenario("should skip validation when validate=false") {
            // Given - JSON with invalid namespace (blank)
            val json = """
                {
                    "namespace": "",
                    "id": "workflow-1",
                    "version": 1,
                    "name": "Invalid Workflow",
                    "description": "Should skip validation",
                    "steps": [
                        {
                            "type": "LogTask",
                            "message": "Test"
                        }
                    ],
                    "createdAt": "2024-01-01T00:00:00Z",
                    "updatedAt": "2024-01-01T00:00:00Z"
                }
            """.trimIndent()

            // When
            val revision = parser.parseRevision(json, validate = false)

            // Then - Should parse successfully without validation
            revision.namespace shouldBe ""
            revision.id shouldBe "workflow-1"
        }

        scenario("should validate version is positive") {
            // Given - JSON with invalid version (0)
            val json = """
                {
                    "namespace": "test-ns",
                    "id": "workflow-1",
                    "version": 0,
                    "name": "Invalid Version Workflow",
                    "description": "Should fail validation",
                    "steps": [
                        {
                            "type": "LogTask",
                            "message": "Test"
                        }
                    ],
                    "createdAt": "2024-01-01T00:00:00Z",
                    "updatedAt": "2024-01-01T00:00:00Z"
                }
            """.trimIndent()

            // When/Then
            val exception = shouldThrow<WorkflowRevisionParsingException> {
                parser.parseRevision(json)
            }
            exception.cause.shouldNotBeNull()
            (exception.cause is InvalidWorkflowRevisionException) shouldBe true
        }

        scenario("should validate name is not blank") {
            // Given - JSON with blank name
            val json = """
                {
                    "namespace": "test-ns",
                    "id": "workflow-1",
                    "version": 1,
                    "name": "",
                    "description": "Should fail validation",
                    "steps": [
                        {
                            "type": "LogTask",
                            "message": "Test"
                        }
                    ],
                    "createdAt": "2024-01-01T00:00:00Z",
                    "updatedAt": "2024-01-01T00:00:00Z"
                }
            """.trimIndent()

            // When/Then
            val exception = shouldThrow<WorkflowRevisionParsingException> {
                parser.parseRevision(json)
            }
            exception.cause.shouldNotBeNull()
            (exception.cause is InvalidWorkflowRevisionException) shouldBe true
        }
    }

    feature("parseRevision - malformed JSON") {
        scenario("should throw WorkflowRevisionParsingException for invalid JSON syntax") {
            // Given - Invalid JSON syntax
            val json = """
                {
                    "namespace": "test-ns",
                    "id": "workflow-1",
                    "version": 1,
                    "name": "Invalid JSON",
                    "description": "Should fail",
                    "steps": [
                        {
                            "type": "LogTask",
                            "message": "Test"
                        }
                    ],
                    "invalid": [unclosed bracket
                }
            """.trimIndent()

            // When/Then
            val exception = shouldThrow<WorkflowRevisionParsingException> {
                parser.parseRevision(json)
            }
            exception.message.shouldNotBeNull()
            exception.message shouldContain "Invalid JSON syntax"
            exception.cause.shouldNotBeNull()
        }

        scenario("should throw WorkflowRevisionParsingException for missing required fields") {
            // Given - JSON missing required fields
            val json = """
                {
                    "namespace": "test-ns",
                    "id": "workflow-1"
                }
            """.trimIndent()

            // When/Then
            val exception = shouldThrow<WorkflowRevisionParsingException> {
                parser.parseRevision(json)
            }
            exception.message.shouldNotBeNull()
            exception.message shouldContain "Invalid JSON syntax"
        }

        scenario("should throw WorkflowRevisionParsingException for empty JSON") {
            // Given
            val json = ""

            // When/Then
            val exception = shouldThrow<WorkflowRevisionParsingException> {
                parser.parseRevision(json)
            }
            exception.message.shouldNotBeNull()
            exception.message shouldContain "Invalid JSON syntax"
        }
    }

    feature("toJson - serialization") {
        scenario("should serialize WorkflowRevision to JSON") {
            // Given
            val revision = WorkflowRevision.create(
                namespace = "test-ns",
                id = "workflow-1",
                version = 1,
                name = "Test Workflow",
                description = "A test workflow",
                steps = listOf(LogTask("Hello, World!")),
                active = false,
                createdAt = Instant.parse("2024-01-01T00:00:00Z"),
                updatedAt = Instant.parse("2024-01-01T00:00:00Z")
            )

            // When
            val json = parser.toJson(revision)

            // Then
            json.shouldNotBeNull()
            json shouldContain "\"namespace\":\"test-ns\""
            json shouldContain "\"id\":\"workflow-1\""
            json shouldContain "\"version\":1"
            json shouldContain "\"name\":\"Test Workflow\""
            json shouldContain "\"description\":\"A test workflow\""
            json shouldContain "\"active\":false"
        }

        scenario("should serialize WorkflowRevisionID to JSON") {
            // Given
            val revisionID = WorkflowRevisionID(
                namespace = "test-ns",
                id = "workflow-1",
                version = 1
            )

            // When
            val json = parser.toJson(revisionID)

            // Then
            json.shouldNotBeNull()
            json shouldContain "\"namespace\":\"test-ns\""
            json shouldContain "\"id\":\"workflow-1\""
            json shouldContain "\"version\":1"
        }

        scenario("should serialize workflow with Sequence to JSON") {
            // Given
            val revision = WorkflowRevision.create(
                namespace = "test-ns",
                id = "workflow-1",
                version = 1,
                name = "Sequence Workflow",
                description = "A workflow with sequence",
                steps = listOf(
                    Sequence(
                        steps = listOf(
                            LogTask("First task"),
                            LogTask("Second task")
                        )
                    )
                ),
                active = false,
                createdAt = Instant.parse("2024-01-01T00:00:00Z"),
                updatedAt = Instant.parse("2024-01-01T00:00:00Z")
            )

            // When
            val json = parser.toJson(revision)

            // Then
            json.shouldNotBeNull()
            json shouldContain "Sequence"
            json shouldContain "LogTask"
            json shouldContain "\"message\":\"First task\""
            json shouldContain "\"message\":\"Second task\""
        }
    }

    feature("round-trip parsing") {
        scenario("should parse and serialize back to equivalent JSON") {
            // Given
            val originalJson = """
                {
                    "namespace": "test-ns",
                    "id": "workflow-1",
                    "version": 1,
                    "name": "Round Trip Test",
                    "description": "Testing round-trip",
                    "steps": [
                        {
                            "type": "LogTask",
                            "message": "Test message"
                        }
                    ],
                    "active": false,
                    "createdAt": "2024-01-01T00:00:00Z",
                    "updatedAt": "2024-01-01T00:00:00Z"
                }
            """.trimIndent()

            // When
            val revision = parser.parseRevision(originalJson)
            val serializedJson = parser.toJson(revision)
            val roundTripRevision = parser.parseRevision(serializedJson)

            // Then
            roundTripRevision.namespace shouldBe revision.namespace
            roundTripRevision.id shouldBe revision.id
            roundTripRevision.version shouldBe revision.version
            roundTripRevision.name shouldBe revision.name
            roundTripRevision.description shouldBe revision.description
            roundTripRevision.active shouldBe revision.active
            roundTripRevision.steps shouldBe revision.steps
        }

        scenario("should handle round-trip with nested structures") {
            // Given
            val originalJson = """
                {
                    "namespace": "test-ns",
                    "id": "workflow-1",
                    "version": 1,
                    "name": "Nested Round Trip",
                    "description": "Testing nested round-trip",
                    "steps": [
                        {
                            "type": "Sequence",
                            "steps": [
                                {
                                    "type": "LogTask",
                                    "message": "Task 1"
                                },
                                {
                                    "type": "Sequence",
                                    "steps": [
                                        {
                                            "type": "LogTask",
                                            "message": "Nested task"
                                        }
                                    ]
                                },
                                {
                                    "type": "LogTask",
                                    "message": "Task 2"
                                }
                            ]
                        }
                    ],
                    "active": true,
                    "createdAt": "2024-01-01T00:00:00Z",
                    "updatedAt": "2024-01-01T00:00:00Z"
                }
            """.trimIndent()

            // When
            val revision = parser.parseRevision(originalJson)
            val serializedJson = parser.toJson(revision)
            val roundTripRevision = parser.parseRevision(serializedJson)

            // Then
            roundTripRevision.steps.size shouldBe revision.steps.size
            val originalSequence = revision.steps[0] as Sequence
            val roundTripSequence = roundTripRevision.steps[0] as Sequence
            roundTripSequence.steps.size shouldBe originalSequence.steps.size
        }
    }
})
