package io.maestro.core.workflows

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.maestro.core.errors.WorkflowRevisionParsingException
import io.maestro.core.parameters.ParameterTypeRegistry
import io.maestro.core.workflows.steps.If
import io.maestro.core.workflows.steps.LogTask
import io.maestro.core.workflows.steps.Sequence
import io.maestro.model.errors.InvalidWorkflowRevisionException
import io.maestro.model.WorkflowRevision
import io.maestro.model.WorkflowRevisionID
import java.time.Instant

/**
 * Unit tests for WorkflowYamlParser.
 *
 * Tests verify:
 * - Parsing valid YAML workflow definitions
 * - Parsing various step types (LogTask, Sequence, If)
 * - Handling malformed YAML syntax
 * - Handling invalid workflow data (validation failures)
 * - Serialization to YAML (round-trip)
 * - Validation control (enabled/disabled)
 */
class WorkflowYamlParserUnitTest : FeatureSpec({

    val parameterTypeRegistry = ParameterTypeRegistry()
    val parser = WorkflowYamlParser(parameterTypeRegistry)

    feature("parseRevision - valid YAML") {
        scenario("should parse simple workflow with LogTask") {
            // Given
            val yaml = """
                namespace: test-ns
                id: workflow-1
                version: 1
                name: Test Workflow
                description: A simple test workflow
                steps:
                  - type: LogTask
                    message: Hello, World!
                active: false
                createdAt: 2024-01-01T00:00:00Z
                updatedAt: 2024-01-01T00:00:00Z
            """.trimIndent()

            // When
            val revision = parser.parseRevision(yaml)

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
            val yaml = """
                namespace: test-ns
                id: workflow-1
                version: 1
                name: Sequence Workflow
                description: A workflow with sequence
                steps:
                  - type: Sequence
                    steps:
                    - type: LogTask
                      message: First task
                    - type: LogTask
                      message: Second task
                active: true
                createdAt: 2024-01-01T00:00:00Z
                updatedAt: 2024-01-01T00:00:00Z
            """.trimIndent()

            // When
            val revision = parser.parseRevision(yaml)

            // Then
            revision.steps.size shouldBe 1
            val sequence = revision.steps[0] as Sequence
            sequence.steps.size shouldBe 2
            sequence.steps[0] shouldBe LogTask("First task")
            sequence.steps[1] shouldBe LogTask("Second task")
        }

        scenario("should parse workflow with If step") {
            // Given
            val yaml = """
                namespace: test-ns
                id: workflow-1
                version: 1
                name: Conditional Workflow
                description: A workflow with conditional logic
                steps:
                  - type: If
                    condition: "${'$'}{env.ENABLE_FEATURE}"
                    ifTrue:
                      type: LogTask
                      message: Feature enabled
                    ifFalse:
                      type: LogTask
                      message: Feature disabled
                active: false
                createdAt: 2024-01-01T00:00:00Z
                updatedAt: 2024-01-01T00:00:00Z
            """.trimIndent()

            // When
            val revision = parser.parseRevision(yaml)

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
            val yaml = """
                namespace: test-ns
                id: workflow-1
                version: 1
                name: Nested Sequence Workflow
                description: A workflow with nested sequences
                steps:
                  - type: Sequence
                    steps:
                      - type: LogTask
                        message: Before nested
                      - type: Sequence
                        steps:
                          - type: LogTask
                            message: Nested task 1
                          - type: LogTask
                            message: Nested task 2
                      - type: LogTask
                        message: After nested
                active: false
                createdAt: 2024-01-01T00:00:00Z
                updatedAt: 2024-01-01T00:00:00Z
            """.trimIndent()

            // When
            val revision = parser.parseRevision(yaml)

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
            // Given - YAML without active, createdAt, updatedAt (should use defaults)
            val yaml = """
                namespace: test-ns
                id: workflow-1
                version: 1
                name: Default Values Workflow
                description: Testing defaults
                steps:
                  - type: LogTask
                    message: Test
                createdAt: 2024-01-01T00:00:00Z
                updatedAt: 2024-01-01T00:00:00Z
            """.trimIndent()

            // When
            val revision = parser.parseRevision(yaml)

            // Then
            revision.active shouldBe false // Default value
        }
    }

    feature("parseRevision - validation") {
        scenario("should validate workflow data by default") {
            // Given - YAML with invalid namespace (blank)
            val yaml = """
                namespace: ""
                id: workflow-1
                version: 1
                name: Invalid Workflow
                description: Should fail validation
                steps:
                  - type: LogTask
                    message: Test
                createdAt: 2024-01-01T00:00:00Z
                updatedAt: 2024-01-01T00:00:00Z
            """.trimIndent()

            // When/Then
            val exception = shouldThrow<WorkflowRevisionParsingException> {
                parser.parseRevision(yaml)
            }
            exception.cause.shouldNotBeNull()
            (exception.cause is InvalidWorkflowRevisionException) shouldBe true
        }

        scenario("should skip validation when validate=false") {
            // Given - YAML with invalid namespace (blank)
            val yaml = """
                namespace: ""
                id: workflow-1
                version: 1
                name: Invalid Workflow
                description: Should skip validation
                steps:
                  - type: LogTask
                    message: Test
                createdAt: 2024-01-01T00:00:00Z
                updatedAt: 2024-01-01T00:00:00Z
            """.trimIndent()

            // When
            val revision = parser.parseRevision(yaml, validate = false)

            // Then - Should parse successfully without validation
            revision.namespace shouldBe ""
            revision.id shouldBe "workflow-1"
        }

        scenario("should validate version is positive") {
            // Given - YAML with invalid version (0)
            val yaml = """
                namespace: test-ns
                id: workflow-1
                version: 0
                name: Invalid Version Workflow
                description: Should fail validation
                steps:
                  - type: LogTask
                    message: Test
                createdAt: 2024-01-01T00:00:00Z
                updatedAt: 2024-01-01T00:00:00Z
            """.trimIndent()

            // When/Then
            val exception = shouldThrow<WorkflowRevisionParsingException> {
                parser.parseRevision(yaml)
            }
            exception.cause.shouldNotBeNull()
            (exception.cause is InvalidWorkflowRevisionException) shouldBe true
        }

        scenario("should validate name is not blank") {
            // Given - YAML with blank name
            val yaml = """
                namespace: test-ns
                id: workflow-1
                version: 1
                name: ""
                description: Should fail validation
                steps:
                  - type: LogTask
                    message: Test
                createdAt: 2024-01-01T00:00:00Z
                updatedAt: 2024-01-01T00:00:00Z
            """.trimIndent()

            // When/Then
            val exception = shouldThrow<WorkflowRevisionParsingException> {
                parser.parseRevision(yaml)
            }
            exception.cause.shouldNotBeNull()
            (exception.cause is InvalidWorkflowRevisionException) shouldBe true
        }
    }

    feature("parseRevision - malformed YAML") {
        scenario("should throw WorkflowRevisionParsingException for invalid YAML syntax") {
            // Given - Invalid YAML syntax
            val yaml = """
                namespace: test-ns
                id: workflow-1
                version: 1
                name: Invalid YAML
                description: Should fail
                steps:
                  - type: LogTask
                    message: Test
                invalid: [unclosed bracket
            """.trimIndent()

            // When/Then
            val exception = shouldThrow<WorkflowRevisionParsingException> {
                parser.parseRevision(yaml)
            }
            exception.message.shouldNotBeNull()
            exception.message shouldContain "Invalid YAML syntax"
            exception.cause.shouldNotBeNull()
        }

        scenario("should throw WorkflowRevisionParsingException for missing required fields") {
            // Given - YAML missing required fields
            val yaml = """
                namespace: test-ns
                id: workflow-1
                # Missing version, name, description, steps, timestamps
            """.trimIndent()

            // When/Then
            val exception = shouldThrow<WorkflowRevisionParsingException> {
                parser.parseRevision(yaml)
            }
            exception.message.shouldNotBeNull()
            exception.message shouldContain "Invalid YAML syntax"
        }

        scenario("should throw WorkflowRevisionParsingException for empty YAML") {
            // Given
            val yaml = ""

            // When/Then
            val exception = shouldThrow<WorkflowRevisionParsingException> {
                parser.parseRevision(yaml)
            }
            exception.message.shouldNotBeNull()
            exception.message shouldContain "Invalid YAML syntax"
        }
    }

    feature("toYaml - serialization") {
        scenario("should serialize WorkflowRevision to YAML") {
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
            val yaml = parser.toYaml(revision)

            // Then
            yaml.shouldNotBeNull()
            yaml shouldContain "namespace: test-ns"
            yaml shouldContain "id: workflow-1"
            yaml shouldContain "version: 1"
            yaml shouldContain "name: Test Workflow"
            yaml shouldContain "description: A test workflow"
            yaml shouldContain "active: false"
        }

        scenario("should serialize WorkflowRevisionID to YAML") {
            // Given
            val revisionID = WorkflowRevisionID(
                namespace = "test-ns",
                id = "workflow-1",
                version = 1
            )

            // When
            val yaml = parser.toYaml(revisionID)

            // Then
            yaml.shouldNotBeNull()
            yaml shouldContain "namespace: test-ns"
            yaml shouldContain "id: workflow-1"
            yaml shouldContain "version: 1"
        }

        scenario("should serialize workflow with Sequence to YAML") {
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
            val yaml = parser.toYaml(revision)
            println(yaml)

            // Then
            yaml.shouldNotBeNull()
            // Jackson serializes with YAML type tags (!<Sequence>, !<LogTask>) or type property
            (yaml.contains("Sequence") || yaml.contains("!<Sequence>")) shouldBe true
            (yaml.contains("LogTask") || yaml.contains("!<LogTask>")) shouldBe true
            yaml shouldContain "message: First task"
            yaml shouldContain "message: Second task"
        }
    }

    feature("round-trip parsing") {
        scenario("should parse and serialize back to equivalent YAML") {
            // Given
            val originalYaml = """
                namespace: test-ns
                id: workflow-1
                version: 1
                name: Round Trip Test
                description: Testing round-trip
                steps:
                  - type: LogTask
                    message: Test message
                active: false
                createdAt: 2024-01-01T00:00:00Z
                updatedAt: 2024-01-01T00:00:00Z
            """.trimIndent()

            // When
            val revision = parser.parseRevision(originalYaml)
            val serializedYaml = parser.toYaml(revision)
            val roundTripRevision = parser.parseRevision(serializedYaml)

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
            val originalYaml = """
                namespace: test-ns
                id: workflow-1
                version: 1
                name: Nested Round Trip
                description: Testing nested round-trip
                steps:
                  - type: Sequence
                    steps:
                    - type: LogTask
                      message: Task 1
                    - type: Sequence
                      steps:
                        - type: LogTask
                          message: Nested task
                    - type: LogTask
                      message: Task 2
                active: true
                createdAt: 2024-01-01T00:00:00Z
                updatedAt: 2024-01-01T00:00:00Z
            """.trimIndent()

            // When
            val revision = parser.parseRevision(originalYaml)
            val serializedYaml = parser.toYaml(revision)
            val roundTripRevision = parser.parseRevision(serializedYaml)

            // Then
            roundTripRevision.steps.size shouldBe revision.steps.size
            val originalSequence = revision.steps[0] as Sequence
            val roundTripSequence = roundTripRevision.steps[0] as Sequence
            roundTripSequence.steps.size shouldBe originalSequence.steps.size
        }
    }
})
