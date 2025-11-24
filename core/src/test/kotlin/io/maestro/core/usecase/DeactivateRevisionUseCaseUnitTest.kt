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

class DeactivateRevisionUseCaseUnitTest : FeatureSpec({

    val fixedInstant = Instant.parse("2024-01-01T12:00:00Z")

    feature("Deactivate workflow revision") {

        scenario("should deactivate an active revision") {
            // Given
            val repository = mockk<IWorkflowRevisionRepository>()
            val useCase = DeactivateRevisionUseCase(repository)

            val revisionId = WorkflowRevisionID("test-ns", "workflow-1", 1)
            val deactivatedRevision = WorkflowRevision(
                namespace = "test-ns",
                id = "workflow-1",
                version = 1,
                name = "Test Workflow",
                description = "Test description",
                steps = listOf(LogTask("Test message")),
                active = false, // Now inactive
                createdAt = fixedInstant,
                updatedAt = fixedInstant
            )

            every { repository.deactivate(revisionId) } returns deactivatedRevision

            // When
            val result = useCase.execute("test-ns", "workflow-1", 1)

            // Then
            result.active shouldBe false
            result.namespace shouldBe "test-ns"
            result.id shouldBe "workflow-1"
            result.version shouldBe 1

            verify(exactly = 1) { repository.deactivate(revisionId) }
        }

        scenario("should deactivate using WorkflowRevisionID") {
            // Given
            val repository = mockk<IWorkflowRevisionRepository>()
            val useCase = DeactivateRevisionUseCase(repository)

            val revisionId = WorkflowRevisionID("test-ns", "workflow-1", 2)
            val deactivatedRevision = WorkflowRevision(
                namespace = "test-ns",
                id = "workflow-1",
                version = 2,
                name = "Test Workflow",
                description = "Test description",
                steps = listOf(LogTask("Test message")),
                active = false,
                createdAt = fixedInstant,
                updatedAt = fixedInstant
            )

            every { repository.deactivate(revisionId) } returns deactivatedRevision

            // When
            val result = useCase.execute(revisionId)

            // Then
            result.active shouldBe false
            verify(exactly = 1) { repository.deactivate(revisionId) }
        }

        scenario("should throw exception when revision doesn't exist") {
            // Given
            val repository = mockk<IWorkflowRevisionRepository>()
            val useCase = DeactivateRevisionUseCase(repository)

            val revisionId = WorkflowRevisionID("test-ns", "non-existent", 1)

            every { repository.deactivate(revisionId) } throws WorkflowRevisionNotFoundException(revisionId)

            // When/Then
            shouldThrow<WorkflowRevisionNotFoundException> {
                useCase.execute("test-ns", "non-existent", 1)
            }

            verify(exactly = 1) { repository.deactivate(revisionId) }
        }

        scenario("should allow deactivating an already inactive revision (idempotent)") {
            // Given
            val repository = mockk<IWorkflowRevisionRepository>()
            val useCase = DeactivateRevisionUseCase(repository)

            val revisionId = WorkflowRevisionID("test-ns", "workflow-1", 1)
            val alreadyInactiveRevision = WorkflowRevision(
                namespace = "test-ns",
                id = "workflow-1",
                version = 1,
                name = "Test Workflow",
                description = "Test description",
                steps = listOf(LogTask("Test message")),
                active = false, // Already inactive
                createdAt = fixedInstant,
                updatedAt = fixedInstant
            )

            every { repository.deactivate(revisionId) } returns alreadyInactiveRevision

            // When
            val result = useCase.execute("test-ns", "workflow-1", 1)

            // Then
            result.active shouldBe false
            verify(exactly = 1) { repository.deactivate(revisionId) }
        }

        scenario("should support deactivating one version while others remain active") {
            // Given
            val repository = mockk<IWorkflowRevisionRepository>()
            val useCase = DeactivateRevisionUseCase(repository)

            val revision1Id = WorkflowRevisionID("test-ns", "workflow-1", 1)

            val deactivatedRevision1 = WorkflowRevision(
                namespace = "test-ns",
                id = "workflow-1",
                version = 1,
                name = "Test Workflow",
                description = "Version 1",
                steps = listOf(LogTask("V1")),
                active = false, // Deactivated
                createdAt = fixedInstant,
                updatedAt = fixedInstant
            )

            every { repository.deactivate(revision1Id) } returns deactivatedRevision1

            // When
            val result = useCase.execute("test-ns", "workflow-1", 1)

            // Then - version 1 is now inactive
            result.active shouldBe false
            result.version shouldBe 1

            verify(exactly = 1) { repository.deactivate(revision1Id) }
        }
    }
})
