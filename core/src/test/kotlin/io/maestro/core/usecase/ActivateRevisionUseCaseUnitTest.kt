package io.maestro.core.usecase

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.maestro.core.IWorkflowRevisionRepository
import io.maestro.core.errors.WorkflowRevisionNotFoundException
import io.maestro.core.steps.LogTask
import io.maestro.model.WorkflowRevision
import io.maestro.model.WorkflowRevisionID
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant

class ActivateRevisionUseCaseUnitTest : FeatureSpec({

    val fixedInstant = Instant.parse("2024-01-01T12:00:00Z")

    feature("Activate workflow revision") {

        scenario("should activate an inactive revision") {
            // Given
            val repository = mockk<IWorkflowRevisionRepository>()
            val useCase = ActivateRevisionUseCase(repository)

            val revisionId = WorkflowRevisionID("test-ns", "workflow-1", 1)
            val activatedRevision = WorkflowRevision(
                namespace = "test-ns",
                id = "workflow-1",
                version = 1,
                name = "Test Workflow",
                description = "Test description",
                steps = listOf(LogTask("Test message")),
                active = true, // Now active
                createdAt = fixedInstant,
                updatedAt = fixedInstant
            )

            every { repository.activate(revisionId) } returns activatedRevision

            // When
            val result = useCase.execute("test-ns", "workflow-1", 1)

            // Then
            result.active shouldBe true
            result.namespace shouldBe "test-ns"
            result.id shouldBe "workflow-1"
            result.version shouldBe 1

            verify(exactly = 1) { repository.activate(revisionId) }
        }

        scenario("should activate using WorkflowRevisionID") {
            // Given
            val repository = mockk<IWorkflowRevisionRepository>()
            val useCase = ActivateRevisionUseCase(repository)

            val revisionId = WorkflowRevisionID("test-ns", "workflow-1", 2)
            val activatedRevision = WorkflowRevision(
                namespace = "test-ns",
                id = "workflow-1",
                version = 2,
                name = "Test Workflow",
                description = "Test description",
                steps = listOf(LogTask("Test message")),
                active = true,
                createdAt = fixedInstant,
                updatedAt = fixedInstant
            )

            every { repository.activate(revisionId) } returns activatedRevision

            // When
            val result = useCase.execute(revisionId)

            // Then
            result.active shouldBe true
            verify(exactly = 1) { repository.activate(revisionId) }
        }

        scenario("should throw exception when revision doesn't exist") {
            // Given
            val repository = mockk<IWorkflowRevisionRepository>()
            val useCase = ActivateRevisionUseCase(repository)

            val revisionId = WorkflowRevisionID("test-ns", "non-existent", 1)

            every { repository.activate(revisionId) } throws WorkflowRevisionNotFoundException(revisionId)

            // When/Then
            shouldThrow<WorkflowRevisionNotFoundException> {
                useCase.execute("test-ns", "non-existent", 1)
            }

            verify(exactly = 1) { repository.activate(revisionId) }
        }

        scenario("should allow activating an already active revision (idempotent)") {
            // Given
            val repository = mockk<IWorkflowRevisionRepository>()
            val useCase = ActivateRevisionUseCase(repository)

            val revisionId = WorkflowRevisionID("test-ns", "workflow-1", 1)
            val alreadyActiveRevision = WorkflowRevision(
                namespace = "test-ns",
                id = "workflow-1",
                version = 1,
                name = "Test Workflow",
                description = "Test description",
                steps = listOf(LogTask("Test message")),
                active = true, // Already active
                createdAt = fixedInstant,
                updatedAt = fixedInstant
            )

            every { repository.activate(revisionId) } returns alreadyActiveRevision

            // When
            val result = useCase.execute("test-ns", "workflow-1", 1)

            // Then
            result.active shouldBe true
            verify(exactly = 1) { repository.activate(revisionId) }
        }

        scenario("should support activating multiple versions of same workflow") {
            // Given
            val repository = mockk<IWorkflowRevisionRepository>()
            val useCase = ActivateRevisionUseCase(repository)

            val revision1Id = WorkflowRevisionID("test-ns", "workflow-1", 1)
            val revision2Id = WorkflowRevisionID("test-ns", "workflow-1", 2)

            val revision1 = WorkflowRevision(
                namespace = "test-ns",
                id = "workflow-1",
                version = 1,
                name = "Test Workflow",
                description = "Version 1",
                steps = listOf(LogTask("V1")),
                active = true,
                createdAt = fixedInstant,
                updatedAt = fixedInstant
            )

            val revision2 = WorkflowRevision(
                namespace = "test-ns",
                id = "workflow-1",
                version = 2,
                name = "Test Workflow",
                description = "Version 2",
                steps = listOf(LogTask("V2")),
                active = true,
                createdAt = fixedInstant,
                updatedAt = fixedInstant
            )

            every { repository.activate(revision1Id) } returns revision1
            every { repository.activate(revision2Id) } returns revision2

            // When
            val result1 = useCase.execute("test-ns", "workflow-1", 1)
            val result2 = useCase.execute("test-ns", "workflow-1", 2)

            // Then - both should be active (multi-active support)
            result1.active shouldBe true
            result1.version shouldBe 1
            result2.active shouldBe true
            result2.version shouldBe 2

            verify(exactly = 1) { repository.activate(revision1Id) }
            verify(exactly = 1) { repository.activate(revision2Id) }
        }
    }
})
