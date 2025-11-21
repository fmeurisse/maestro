package io.maestro.model.workflow

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.throwable.shouldHaveMessage
import io.maestro.model.WorkflowRevision
import io.maestro.model.exception.InvalidWorkflowRevision
import io.maestro.model.steps.Step
import java.time.Instant

class WorkflowRevisionUnitTest : FeatureSpec({

    val mockSteps = listOf<Step>(object : Step {})
    val now = Instant.now()

    feature("create method") {
        scenario("should create valid workflow revision with all properties") {
            val revision = WorkflowRevision.validateAndCreate(
                namespace = "production",
                id = "payment-workflow",
                version = 1,
                name = "Payment Processing",
                description = "Handles payment processing",
                steps = mockSteps,
                active = false,
                createdAt = now,
                updatedAt = now
            )

            revision.namespace shouldBe "production"
            revision.id shouldBe "payment-workflow"
            revision.version shouldBe 1
            revision.name shouldBe "Payment Processing"
            revision.description shouldBe "Handles payment processing"
            revision.active shouldBe false
            revision.createdAt shouldBe now
            revision.updatedAt shouldBe now
        }

        scenario("should throw InvalidWorkflowRevision when namespace is blank") {
            val exception = shouldThrow<InvalidWorkflowRevision> {
                WorkflowRevision.validateAndCreate(
                    namespace = "",
                    id = "workflow-1",
                    version = 1,
                    name = "Test",
                    description = "Test",
                    steps = mockSteps
                )
            }
            exception shouldHaveMessage "Namespace must not be blank"
        }

        scenario("should throw InvalidWorkflowRevision when id is blank") {
            val exception = shouldThrow<InvalidWorkflowRevision> {
                WorkflowRevision.validateAndCreate(
                    namespace = "production",
                    id = "",
                    version = 1,
                    name = "Test",
                    description = "Test",
                    steps = mockSteps
                )
            }
            exception shouldHaveMessage "ID must not be blank"
        }

        scenario("should throw InvalidWorkflowRevision when namespace format is invalid") {
            val exception = shouldThrow<InvalidWorkflowRevision> {
                WorkflowRevision.validateAndCreate(
                    namespace = "invalid namespace!",
                    id = "workflow-1",
                    version = 1,
                    name = "Test",
                    description = "Test",
                    steps = mockSteps
                )
            }
            exception.message shouldNotBe null
            exception.message!! shouldBe "Namespace must contain only alphanumeric characters, hyphens, and underscores"
        }

        scenario("should throw InvalidWorkflowRevision when id format is invalid") {
            val exception = shouldThrow<InvalidWorkflowRevision> {
                WorkflowRevision.validateAndCreate(
                    namespace = "production",
                    id = "invalid id!",
                    version = 1,
                    name = "Test",
                    description = "Test",
                    steps = mockSteps
                )
            }
            exception.message shouldNotBe null
            exception.message!! shouldBe "ID must contain only alphanumeric characters, hyphens, and underscores"
        }

        scenario("should throw InvalidWorkflowRevision when version is zero") {
            val exception = shouldThrow<InvalidWorkflowRevision> {
                WorkflowRevision.validateAndCreate(
                    namespace = "production",
                    id = "workflow-1",
                    version = 0,
                    name = "Test",
                    description = "Test",
                    steps = mockSteps
                )
            }
            exception shouldHaveMessage "Version must be positive"
        }

        scenario("should throw InvalidWorkflowRevision when version is negative") {
            val exception = shouldThrow<InvalidWorkflowRevision> {
                WorkflowRevision.validateAndCreate(
                    namespace = "production",
                    id = "workflow-1",
                    version = -1,
                    name = "Test",
                    description = "Test",
                    steps = mockSteps
                )
            }
            exception shouldHaveMessage "Version must be positive"
        }

        scenario("should throw InvalidWorkflowRevision when name is blank") {
            val exception = shouldThrow<InvalidWorkflowRevision> {
                WorkflowRevision.validateAndCreate(
                    namespace = "production",
                    id = "workflow-1",
                    version = 1,
                    name = "",
                    description = "Test",
                    steps = mockSteps
                )
            }
            exception shouldHaveMessage "Name must not be blank"
        }

        scenario("should throw InvalidWorkflowRevision when namespace exceeds maximum length") {
            val exception = shouldThrow<InvalidWorkflowRevision> {
                WorkflowRevision.validateAndCreate(
                    namespace = "a".repeat(101),
                    id = "workflow-1",
                    version = 1,
                    name = "Test",
                    description = "Test",
                    steps = mockSteps
                )
            }
            exception.message shouldNotBe null
            exception.message!! shouldBe "Namespace must not exceed 100 characters"
        }

        scenario("should throw InvalidWorkflowRevision when id exceeds maximum length") {
            val exception = shouldThrow<InvalidWorkflowRevision> {
                WorkflowRevision.validateAndCreate(
                    namespace = "production",
                    id = "a".repeat(101),
                    version = 1,
                    name = "Test",
                    description = "Test",
                    steps = mockSteps
                )
            }
            exception.message shouldNotBe null
            exception.message!! shouldBe "ID must not exceed 100 characters"
        }

        scenario("should throw InvalidWorkflowRevision when name exceeds maximum length") {
            val exception = shouldThrow<InvalidWorkflowRevision> {
                WorkflowRevision.validateAndCreate(
                    namespace = "production",
                    id = "workflow-1",
                    version = 1,
                    name = "a".repeat(256),
                    description = "Test",
                    steps = mockSteps
                )
            }
            exception.message shouldNotBe null
            exception.message!! shouldBe "Name must not exceed 255 characters"
        }

        scenario("should throw InvalidWorkflowRevision when description exceeds maximum length") {
            val exception = shouldThrow<InvalidWorkflowRevision> {
                WorkflowRevision.validateAndCreate(
                    namespace = "production",
                    id = "workflow-1",
                    version = 1,
                    name = "Test",
                    description = "a".repeat(1001),
                    steps = mockSteps
                )
            }
            exception.message shouldNotBe null
            exception.message!! shouldBe "Description must not exceed 1000 characters"
        }

        scenario("should create revision with maximum length description") {
            val revision = WorkflowRevision.validateAndCreate(
                namespace = "production",
                id = "workflow-1",
                version = 1,
                name = "Test",
                description = "a".repeat(1000),
                steps = mockSteps
            )
            revision.description.length shouldBe 1000
        }

        scenario("should accept namespace with hyphens and underscores") {
            val revision = WorkflowRevision.validateAndCreate(
                namespace = "prod-staging_test",
                id = "workflow-1",
                version = 1,
                name = "Test",
                description = "Test",
                steps = mockSteps
            )
            revision.namespace shouldBe "prod-staging_test"
        }

        scenario("should accept id with hyphens and underscores") {
            val revision = WorkflowRevision.validateAndCreate(
                namespace = "production",
                id = "payment_processing-v2",
                version = 1,
                name = "Test",
                description = "Test",
                steps = mockSteps
            )
            revision.id shouldBe "payment_processing-v2"
        }
    }

    feature("activate method") {
        scenario("should change active flag to true and update timestamp") {
            val revision = WorkflowRevision.validateAndCreate(
                namespace = "production",
                id = "workflow-1",
                version = 1,
                name = "Test",
                description = "Test",
                steps = mockSteps,
                active = false,
                createdAt = now,
                updatedAt = now
            )

            val activated = revision.activate()

            activated.active shouldBe true
            activated.updatedAt shouldNotBe now
            activated.namespace shouldBe revision.namespace
            activated.version shouldBe revision.version
        }
    }

    feature("deactivate method") {
        scenario("should change active flag to false and update timestamp") {
            val revision = WorkflowRevision.validateAndCreate(
                namespace = "production",
                id = "workflow-1",
                version = 1,
                name = "Test",
                description = "Test",
                steps = mockSteps,
                active = true,
                createdAt = now,
                updatedAt = now
            )

            val deactivated = revision.deactivate()

            deactivated.active shouldBe false
            deactivated.updatedAt shouldNotBe now
        }
    }

    feature("withUpdatedTimestamp method") {
        scenario("should update only timestamp while preserving other properties") {
            val revision = WorkflowRevision.validateAndCreate(
                namespace = "production",
                id = "workflow-1",
                version = 1,
                name = "Test",
                description = "Test",
                steps = mockSteps,
                createdAt = now,
                updatedAt = now
            )

            val updated = revision.withUpdatedTimestamp()

            updated.updatedAt shouldNotBe now
            updated.namespace shouldBe revision.namespace
            updated.active shouldBe revision.active
        }
    }

    feature("revisionId method") {
        scenario("should return correct composite identifier") {
            val revision = WorkflowRevision.validateAndCreate(
                namespace = "production",
                id = "workflow-1",
                version = 2,
                name = "Test",
                description = "Test",
                steps = mockSteps
            )

            val revisionId = revision.revisionId()

            revisionId.namespace shouldBe "production"
            revisionId.id shouldBe "workflow-1"
            revisionId.version shouldBe 2
        }
    }

    feature("workflowId method") {
        scenario("should return identifier without version") {
            val revision = WorkflowRevision.validateAndCreate(
                namespace = "production",
                id = "workflow-1",
                version = 2,
                name = "Test",
                description = "Test",
                steps = mockSteps
            )

            val workflowId = revision.workflowId()

            workflowId.namespace shouldBe "production"
            workflowId.id shouldBe "workflow-1"
        }
    }
})
