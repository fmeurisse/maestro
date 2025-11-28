package io.maestro.core.workflows.usecases

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.maestro.core.workflows.IWorkflowRevisionRepository
import io.maestro.core.errors.ActiveRevisionConflictException
import io.maestro.core.errors.WorkflowRevisionNotFoundException
import io.maestro.core.workflows.steps.LogTask
import io.maestro.model.WorkflowRevision
import io.maestro.model.WorkflowRevisionID
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.time.Instant

class DeleteRevisionUseCaseUnitTest : FeatureSpec({

    feature("Delete workflow revision") {

        scenario("should delete an inactive revision successfully") {
            // Given
            val repository = mockk<IWorkflowRevisionRepository>()
            val useCase = DeleteRevisionUseCase(repository)

            val revisionId = WorkflowRevisionID("test-ns", "workflow-1", 1)
            val inactiveRevision = WorkflowRevision(
                namespace = "test-ns",
                id = "workflow-1",
                version = 1,
                name = "Test Workflow",
                description = "Test",
                steps = listOf(LogTask("Test")),
                active = false,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )

            every { repository.findById(revisionId) } returns inactiveRevision
            every { repository.deleteById(revisionId) } just runs

            // When
            useCase.execute("test-ns", "workflow-1", 1)

            // Then
            verify(exactly = 1) { repository.findById(revisionId) }
            verify(exactly = 1) { repository.deleteById(revisionId) }
        }

        scenario("should delete using WorkflowRevisionID overload") {
            // Given
            val repository = mockk<IWorkflowRevisionRepository>()
            val useCase = DeleteRevisionUseCase(repository)

            val revisionId = WorkflowRevisionID("test-ns", "workflow-1", 2)
            val inactiveRevision = WorkflowRevision(
                namespace = "test-ns",
                id = "workflow-1",
                version = 2,
                name = "Test Workflow",
                description = "Test",
                steps = listOf(LogTask("Test")),
                active = false,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )

            every { repository.findById(revisionId) } returns inactiveRevision
            every { repository.deleteById(revisionId) } just runs

            // When
            useCase.execute(revisionId)

            // Then
            verify(exactly = 1) { repository.findById(revisionId) }
            verify(exactly = 1) { repository.deleteById(revisionId) }
        }

        scenario("should throw exception when deleting active revision") {
            // Given
            val repository = mockk<IWorkflowRevisionRepository>()
            val useCase = DeleteRevisionUseCase(repository)

            val revisionId = WorkflowRevisionID("test-ns", "workflow-1", 1)
            val activeRevision = WorkflowRevision(
                namespace = "test-ns",
                id = "workflow-1",
                version = 1,
                name = "Active Workflow",
                description = "Test",
                steps = listOf(LogTask("Test")),
                active = true,  // Active!
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )

            every { repository.findById(revisionId) } returns activeRevision

            // When/Then
            shouldThrow<ActiveRevisionConflictException> {
                useCase.execute("test-ns", "workflow-1", 1)
            }

            verify(exactly = 1) { repository.findById(revisionId) }
            verify(exactly = 0) { repository.deleteById(any()) }
        }

        scenario("should throw exception when revision doesn't exist") {
            // Given
            val repository = mockk<IWorkflowRevisionRepository>()
            val useCase = DeleteRevisionUseCase(repository)

            val revisionId = WorkflowRevisionID("test-ns", "non-existent", 1)

            every { repository.findById(revisionId) } returns null

            // When/Then
            shouldThrow<WorkflowRevisionNotFoundException> {
                useCase.execute("test-ns", "non-existent", 1)
            }

            verify(exactly = 1) { repository.findById(revisionId) }
            verify(exactly = 0) { repository.deleteById(any()) }
        }

        scenario("should allow deletion after deactivation") {
            // Given
            val repository = mockk<IWorkflowRevisionRepository>()
            val useCase = DeleteRevisionUseCase(repository)

            val revisionId = WorkflowRevisionID("test-ns", "workflow-1", 1)
            val deactivatedRevision = WorkflowRevision(
                namespace = "test-ns",
                id = "workflow-1",
                version = 1,
                name = "Deactivated Workflow",
                description = "Test",
                steps = listOf(LogTask("Test")),
                active = false,  // Was active, now deactivated
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )

            every { repository.findById(revisionId) } returns deactivatedRevision
            every { repository.deleteById(revisionId) } just runs

            // When
            useCase.execute("test-ns", "workflow-1", 1)

            // Then - should complete without exception
            verify(exactly = 1) { repository.findById(revisionId) }
            verify(exactly = 1) { repository.deleteById(revisionId) }
        }
    }
})
