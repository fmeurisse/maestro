package io.maestro.model.workflow

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.throwable.shouldHaveMessage
import io.maestro.model.WorkflowRevision
import io.maestro.model.WorkflowRevisionWithSource
import io.maestro.model.errors.InvalidWorkflowRevisionException
import io.maestro.model.steps.Step

class WorkflowRevisionWithSourceUnitTest : FeatureSpec({

    val mockSteps = listOf<Step>(object : Step {})
    val yamlSource = """
        namespace: production
        id: workflow-1
        name: Test Workflow
        description: Test
        steps:
          type: Sequence
          steps: []
    """.trimIndent()

    feature("create method") {
        scenario("should create valid workflow revision with source and all properties") {
            val revisionWithSource = WorkflowRevisionWithSource.create(
                namespace = "production",
                id = "workflow-1",
                version = 1,
                name = "Test",
                description = "Test",
                yamlSource = yamlSource,
                steps = mockSteps
            )

            revisionWithSource.namespace shouldBe "production"
            revisionWithSource.id shouldBe "workflow-1"
            revisionWithSource.version shouldBe 1
            revisionWithSource.name shouldBe "Test"
            revisionWithSource.yamlSource shouldBe yamlSource
            revisionWithSource.active shouldBe false
        }

        scenario("should throw InvalidWorkflowRevision when yaml source is blank") {
            val exception = shouldThrow<InvalidWorkflowRevisionException> {
                WorkflowRevisionWithSource.create(
                    namespace = "production",
                    id = "workflow-1",
                    version = 1,
                    name = "Test",
                    description = "Test",
                    yamlSource = "",
                    steps = mockSteps
                )
            }
            exception shouldHaveMessage "YAML source must not be blank"
        }

        scenario("should throw InvalidWorkflowRevision when yaml source is whitespace-only") {
            val exception = shouldThrow<InvalidWorkflowRevisionException> {
                WorkflowRevisionWithSource.create(
                    namespace = "production",
                    id = "workflow-1",
                    version = 1,
                    name = "Test",
                    description = "Test",
                    yamlSource = "   \n  \t  ",
                    steps = mockSteps
                )
            }
            exception shouldHaveMessage "YAML source must not be blank"
        }

        scenario("should propagate validation errors from revision creation") {
            val exception = shouldThrow<InvalidWorkflowRevisionException> {
                WorkflowRevisionWithSource.create(
                    namespace = "",  // Invalid namespace
                    id = "workflow-1",
                    version = 1,
                    name = "Test",
                    description = "Test",
                    yamlSource = yamlSource,
                    steps = mockSteps
                )
            }
            exception shouldHaveMessage "Namespace must not be blank"
        }
    }

    feature("fromRevision method") {
        scenario("should create instance from existing revision with yaml source") {
            val revision = WorkflowRevision.validateAndCreate(
                namespace = "production",
                id = "workflow-1",
                version = 1,
                name = "Test",
                description = "Test",
                steps = mockSteps
            )

            val withSource = WorkflowRevisionWithSource.fromRevision(revision, yamlSource)

            withSource.namespace shouldBe revision.namespace
            withSource.version shouldBe revision.version
            withSource.yamlSource shouldBe yamlSource
        }

        scenario("should throw InvalidWorkflowRevision when yaml source is blank") {
            val revision = WorkflowRevision.validateAndCreate(
                namespace = "production",
                id = "workflow-1",
                version = 1,
                name = "Test",
                description = "Test",
                steps = mockSteps
            )

            val exception = shouldThrow<InvalidWorkflowRevisionException> {
                WorkflowRevisionWithSource.fromRevision(revision, "")
            }
            exception shouldHaveMessage "YAML source must not be blank"
        }
    }

    feature("toRevision method") {
        scenario("should convert to WorkflowRevision dropping yaml source") {
            val withSource = WorkflowRevisionWithSource.create(
                namespace = "production",
                id = "workflow-1",
                version = 1,
                name = "Test",
                description = "Test",
                yamlSource = yamlSource,
                steps = mockSteps
            )

            val revision = withSource.toRevision()

            revision.namespace shouldBe withSource.namespace
            revision.version shouldBe withSource.version
            revision.name shouldBe withSource.name
            revision.description shouldBe withSource.description
            revision.active shouldBe withSource.active
        }
    }

    feature("convenience accessors") {
        scenario("should delegate to revision properties") {
            val withSource = WorkflowRevisionWithSource.create(
                namespace = "production",
                id = "workflow-1",
                version = 2,
                name = "Test Workflow",
                description = "Test Description",
                yamlSource = yamlSource,
                steps = mockSteps,
                active = true
            )

            withSource.namespace shouldBe "production"
            withSource.id shouldBe "workflow-1"
            withSource.version shouldBe 2
            withSource.name shouldBe "Test Workflow"
            withSource.description shouldBe "Test Description"
            withSource.active shouldBe true
            withSource.steps shouldBe mockSteps
        }
    }

    feature("activate method") {
        scenario("should update revision and preserve yaml source") {
            val withSource = WorkflowRevisionWithSource.create(
                namespace = "production",
                id = "workflow-1",
                version = 1,
                name = "Test",
                description = "Test",
                yamlSource = yamlSource,
                steps = mockSteps,
                active = false
            )

            val activated = withSource.activate()

            activated.active shouldBe true
            activated.yamlSource shouldBe yamlSource
            activated.updatedAt shouldNotBe withSource.updatedAt
        }
    }

    feature("deactivate method") {
        scenario("should update revision and preserve yaml source") {
            val withSource = WorkflowRevisionWithSource.create(
                namespace = "production",
                id = "workflow-1",
                version = 1,
                name = "Test",
                description = "Test",
                yamlSource = yamlSource,
                steps = mockSteps,
                active = true
            )

            val deactivated = withSource.deactivate()

            deactivated.active shouldBe false
            deactivated.yamlSource shouldBe yamlSource
        }
    }

    feature("updateContent method") {
        scenario("should update yaml source, steps, and description") {
            val withSource = WorkflowRevisionWithSource.create(
                namespace = "production",
                id = "workflow-1",
                version = 1,
                name = "Test",
                description = "Original description",
                yamlSource = yamlSource,
                steps = mockSteps
            )

            val newSteps = listOf<Step>(object : Step {})
            val newYaml = "namespace: production\nid: workflow-1\nupdated: true"
            val updated = withSource.updateContent(newYaml, newSteps, "New description")

            updated.yamlSource shouldBe newYaml
            updated.steps shouldBe newSteps
            updated.description shouldBe "New description"
            updated.updatedAt shouldNotBe withSource.updatedAt
        }

        scenario("should preserve original description when new description is null") {
            val withSource = WorkflowRevisionWithSource.create(
                namespace = "production",
                id = "workflow-1",
                version = 1,
                name = "Test",
                description = "Original description",
                yamlSource = yamlSource,
                steps = mockSteps
            )

            val newSteps = listOf<Step>(object : Step {})
            val newYaml = "updated yaml"
            val updated = withSource.updateContent(newYaml, newSteps, null)

            updated.description shouldBe "Original description"
            updated.yamlSource shouldBe newYaml
        }

        scenario("should throw InvalidWorkflowRevision when yaml source is blank") {
            val withSource = WorkflowRevisionWithSource.create(
                namespace = "production",
                id = "workflow-1",
                version = 1,
                name = "Test",
                description = "Test",
                yamlSource = yamlSource,
                steps = mockSteps
            )

            val exception = shouldThrow<InvalidWorkflowRevisionException> {
                withSource.updateContent("", mockSteps)
            }
            exception shouldHaveMessage "YAML source must not be blank"
        }
    }

    feature("revisionId method") {
        scenario("should return correct composite identifier") {
            val withSource = WorkflowRevisionWithSource.create(
                namespace = "production",
                id = "workflow-1",
                version = 3,
                name = "Test",
                description = "Test",
                yamlSource = yamlSource,
                steps = mockSteps
            )

            val revisionId = withSource.revisionId()

            revisionId.namespace shouldBe "production"
            revisionId.id shouldBe "workflow-1"
            revisionId.version shouldBe 3
        }
    }

    feature("workflowId method") {
        scenario("should return identifier without version") {
            val withSource = WorkflowRevisionWithSource.create(
                namespace = "staging",
                id = "test-workflow",
                version = 1,
                name = "Test",
                description = "Test",
                yamlSource = yamlSource,
                steps = mockSteps
            )

            val workflowId = withSource.workflowId()

            workflowId.namespace shouldBe "staging"
            workflowId.id shouldBe "test-workflow"
        }
    }

    feature("yaml source handling") {
        scenario("should preserve multiline content") {
            val multilineYaml = """
                namespace: production
                id: complex-workflow
                name: Complex Workflow
                description: |
                  This is a multiline description
                  with multiple lines
                  and indentation
                steps:
                  type: Sequence
                  steps:
                    - type: LogTask
                      message: "Step 1"
                    - type: LogTask
                      message: "Step 2"
            """.trimIndent()

            val withSource = WorkflowRevisionWithSource.create(
                namespace = "production",
                id = "complex-workflow",
                version = 1,
                name = "Complex Workflow",
                description = "Multiline description",
                yamlSource = multilineYaml,
                steps = mockSteps
            )

            withSource.yamlSource shouldBe multilineYaml
        }
    }
})
