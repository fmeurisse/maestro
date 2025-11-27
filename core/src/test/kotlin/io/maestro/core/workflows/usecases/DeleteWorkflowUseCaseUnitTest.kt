package io.maestro.core.workflows.usecases

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.maestro.core.workflows.IWorkflowRevisionRepository
import io.maestro.core.errors.ActiveRevisionConflictException
import io.maestro.core.workflows.steps.LogTask
import io.maestro.model.WorkflowID
import io.maestro.model.WorkflowRevision
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant

class DeleteWorkflowUseCaseUnitTest : FeatureSpec({

    feature("Delete entire workflow") {

        scenario("should delete all revisions of a workflow successfully") {
            // Given
            val repository = mockk<IWorkflowRevisionRepository>()
            val useCase = DeleteWorkflowUseCase(repository)

            val workflowId = WorkflowID("test-ns", "workflow-1")

            every { repository.findActiveRevisions(workflowId) } returns emptyList()
            every { repository.deleteByWorkflowId(workflowId) } returns 3

            // When
            val deletedCount = useCase.execute("test-ns", "workflow-1")

            // Then
            deletedCount shouldBe 3
            verify(exactly = 1) { repository.findActiveRevisions(workflowId) }
            verify(exactly = 1) { repository.deleteByWorkflowId(workflowId) }
        }

        scenario("should delete using WorkflowID overload") {
            // Given
            val repository = mockk<IWorkflowRevisionRepository>()
            val useCase = DeleteWorkflowUseCase(repository)

            val workflowId = WorkflowID("test-ns", "workflow-2")

            every { repository.findActiveRevisions(workflowId) } returns emptyList()
            every { repository.deleteByWorkflowId(workflowId) } returns 5

            // When
            val deletedCount = useCase.execute(workflowId)

            // Then
            deletedCount shouldBe 5
            verify(exactly = 1) { repository.findActiveRevisions(workflowId) }
            verify(exactly = 1) { repository.deleteByWorkflowId(workflowId) }
        }

        scenario("should return 0 when workflow doesn't exist") {
            // Given
            val repository = mockk<IWorkflowRevisionRepository>()
            val useCase = DeleteWorkflowUseCase(repository)

            val workflowId = WorkflowID("test-ns", "non-existent")

            every { repository.findActiveRevisions(workflowId) } returns emptyList()
            every { repository.deleteByWorkflowId(workflowId) } returns 0

            // When
            val deletedCount = useCase.execute("test-ns", "non-existent")

            // Then
            deletedCount shouldBe 0
            verify(exactly = 1) { repository.findActiveRevisions(workflowId) }
            verify(exactly = 1) { repository.deleteByWorkflowId(workflowId) }
        }

        scenario("should delete workflow with single revision") {
            // Given
            val repository = mockk<IWorkflowRevisionRepository>()
            val useCase = DeleteWorkflowUseCase(repository)

            val workflowId = WorkflowID("test-ns", "workflow-single")

            every { repository.findActiveRevisions(workflowId) } returns emptyList()
            every { repository.deleteByWorkflowId(workflowId) } returns 1

            // When
            val deletedCount = useCase.execute("test-ns", "workflow-single")

            // Then
            deletedCount shouldBe 1
            verify(exactly = 1) { repository.findActiveRevisions(workflowId) }
            verify(exactly = 1) { repository.deleteByWorkflowId(workflowId) }
        }

        scenario("should delete workflow with multiple inactive revisions") {
            // Given
            val repository = mockk<IWorkflowRevisionRepository>()
            val useCase = DeleteWorkflowUseCase(repository)

            val workflowId = WorkflowID("test-ns", "workflow-multi")

            // All revisions are inactive
            every { repository.findActiveRevisions(workflowId) } returns emptyList()
            every { repository.deleteByWorkflowId(workflowId) } returns 10

            // When
            val deletedCount = useCase.execute("test-ns", "workflow-multi")

            // Then
            deletedCount shouldBe 10
            verify(exactly = 1) { repository.findActiveRevisions(workflowId) }
            verify(exactly = 1) { repository.deleteByWorkflowId(workflowId) }
        }

        scenario("should throw exception when workflow has active revisions") {
            // Given
            val repository = mockk<IWorkflowRevisionRepository>()
            val useCase = DeleteWorkflowUseCase(repository)

            val workflowId = WorkflowID("test-ns", "workflow-with-active")
            val activeRevision = WorkflowRevision(
                namespace = "test-ns",
                id = "workflow-with-active",
                version = 2,
                name = "Active Workflow",
                description = "Test",
                steps = listOf(LogTask("Test")),
                active = true,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )

            every { repository.findActiveRevisions(workflowId) } returns listOf(activeRevision)

            // When/Then
            shouldThrow<ActiveRevisionConflictException> {
                useCase.execute("test-ns", "workflow-with-active")
            }

            verify(exactly = 1) { repository.findActiveRevisions(workflowId) }
            verify(exactly = 0) { repository.deleteByWorkflowId(any()) }
        }

        scenario("should throw exception when workflow has multiple active revisions") {
            // Given
            val repository = mockk<IWorkflowRevisionRepository>()
            val useCase = DeleteWorkflowUseCase(repository)

            val workflowId = WorkflowID("test-ns", "workflow-multi-active")
            val activeRevisions = listOf(
                WorkflowRevision(
                    namespace = "test-ns",
                    id = "workflow-multi-active",
                    version = 1,
                    name = "Active Workflow v1",
                    description = "Test",
                    steps = listOf(LogTask("Test")),
                    active = true,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now()
                ),
                WorkflowRevision(
                    namespace = "test-ns",
                    id = "workflow-multi-active",
                    version = 2,
                    name = "Active Workflow v2",
                    description = "Test",
                    steps = listOf(LogTask("Test")),
                    active = true,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now()
                )
            )

            every { repository.findActiveRevisions(workflowId) } returns activeRevisions

            // When/Then
            shouldThrow<ActiveRevisionConflictException> {
                useCase.execute("test-ns", "workflow-multi-active")
            }

            verify(exactly = 1) { repository.findActiveRevisions(workflowId) }
            verify(exactly = 0) { repository.deleteByWorkflowId(any()) }
        }

        scenario("should allow deletion after all revisions are deactivated") {
            // Given
            val repository = mockk<IWorkflowRevisionRepository>()
            val useCase = DeleteWorkflowUseCase(repository)

            val workflowId = WorkflowID("test-ns", "workflow-deactivated")

            // All revisions now inactive
            every { repository.findActiveRevisions(workflowId) } returns emptyList()
            every { repository.deleteByWorkflowId(workflowId) } returns 3

            // When
            val deletedCount = useCase.execute("test-ns", "workflow-deactivated")

            // Then - should complete without exception
            deletedCount shouldBe 3
            verify(exactly = 1) { repository.findActiveRevisions(workflowId) }
            verify(exactly = 1) { repository.deleteByWorkflowId(workflowId) }
        }
    }
})
