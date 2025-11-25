package io.maestro.core.usecase

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.maestro.core.IWorkflowRevisionRepository
import io.maestro.core.errors.WorkflowRevisionNotFoundException
import io.maestro.core.steps.LogTask
import io.maestro.model.WorkflowRevision
import io.maestro.model.WorkflowRevisionID
import io.maestro.model.WorkflowRevisionWithSource
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ActivateRevisionUseCaseUnitTest : FeatureSpec({

    val fixedInstant = Instant.parse("2024-01-01T12:00:00Z")
    val fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    feature("Activate workflow revision") {

        scenario("should activate an inactive revision") {
            // Given
            val repository = mockk<IWorkflowRevisionRepository>()
            val useCase = ActivateRevisionUseCase(repository, fixedClock)

            val revisionId = WorkflowRevisionID("test-ns", "workflow-1", 1)

            val existingRevision = WorkflowRevisionWithSource(
                revision = WorkflowRevision(
                    namespace = "test-ns",
                    id = "workflow-1",
                    version = 1,
                    name = "Test Workflow",
                    description = "Test description",
                    steps = listOf(LogTask("Test message")),
                    active = false,
                    createdAt = fixedInstant,
                    updatedAt = fixedInstant
                ),
                yamlSource = "namespace: test-ns\nid: workflow-1\nversion: 1"
            )

            val activatedRevision = WorkflowRevisionWithSource(
                revision = WorkflowRevision(
                    namespace = "test-ns",
                    id = "workflow-1",
                    version = 1,
                    name = "Test Workflow",
                    description = "Test description",
                    steps = listOf(LogTask("Test message")),
                    active = true,
                    createdAt = fixedInstant,
                    updatedAt = fixedInstant
                ),
                yamlSource = "namespace: test-ns\nid: workflow-1\nversion: 1\nupdatedAt: ${fixedInstant}"
            )

            every { repository.findByIdWithSource(revisionId) } returns existingRevision
            every { repository.activateWithSource(revisionId, any()) } returns activatedRevision

            // When
            val result = useCase.execute("test-ns", "workflow-1", 1, fixedInstant)

            // Then
            result.active shouldBe true
            result.namespace shouldBe "test-ns"
            result.id shouldBe "workflow-1"
            result.version shouldBe 1

            verify(exactly = 1) { repository.findByIdWithSource(revisionId) }
            verify(exactly = 1) { repository.activateWithSource(revisionId, any()) }
        }

        scenario("should activate using WorkflowRevisionID") {
            // Given
            val repository = mockk<IWorkflowRevisionRepository>()
            val useCase = ActivateRevisionUseCase(repository, fixedClock)

            val revisionId = WorkflowRevisionID("test-ns", "workflow-1", 2)

            val existingRevision = WorkflowRevisionWithSource.fromRevision(
                WorkflowRevision(
                    namespace = "test-ns",
                    id = "workflow-1",
                    version = 2,
                    name = "Test Workflow",
                    description = "Test description",
                    steps = listOf(LogTask("Test message")),
                    active = false,
                    createdAt = fixedInstant,
                    updatedAt = fixedInstant
                ),
                "namespace: test-ns\nid: workflow-1\nversion: 2"
            )

            val activatedRevision = WorkflowRevisionWithSource.fromRevision(
                WorkflowRevision(
                    namespace = "test-ns",
                    id = "workflow-1",
                    version = 2,
                    name = "Test Workflow",
                    description = "Test description",
                    steps = listOf(LogTask("Test message")),
                    active = true,
                    createdAt = fixedInstant,
                    updatedAt = fixedInstant
                ),
                "namespace: test-ns\nid: workflow-1\nversion: 2\nupdatedAt: ${fixedInstant}"
            )

            every { repository.findByIdWithSource(revisionId) } returns existingRevision
            every { repository.activateWithSource(revisionId, any()) } returns activatedRevision

            // When
            val result = useCase.execute(revisionId, fixedInstant)

            // Then
            result.active shouldBe true
            verify(exactly = 1) { repository.findByIdWithSource(revisionId) }
            verify(exactly = 1) { repository.activateWithSource(revisionId, any()) }
        }

        scenario("should throw exception when revision doesn't exist") {
            // Given
            val repository = mockk<IWorkflowRevisionRepository>()
            val useCase = ActivateRevisionUseCase(repository, fixedClock)

            val revisionId = WorkflowRevisionID("test-ns", "non-existent", 1)

            every { repository.findByIdWithSource(revisionId) } returns null

            // When/Then
            shouldThrow<WorkflowRevisionNotFoundException> {
                useCase.execute("test-ns", "non-existent", 1, fixedInstant)
            }

            verify(exactly = 1) { repository.findByIdWithSource(revisionId) }
            verify(exactly = 0) { repository.activateWithSource(any(), any()) }
        }

        scenario("should allow activating an already active revision (idempotent)") {
            // Given
            val repository = mockk<IWorkflowRevisionRepository>()
            val useCase = ActivateRevisionUseCase(repository, fixedClock)

            val revisionId = WorkflowRevisionID("test-ns", "workflow-1", 1)

            val existingRevision = WorkflowRevisionWithSource.fromRevision(
                WorkflowRevision(
                    namespace = "test-ns",
                    id = "workflow-1",
                    version = 1,
                    name = "Test Workflow",
                    description = "Test description",
                    steps = listOf(LogTask("Test message")),
                    active = false,
                    createdAt = fixedInstant,
                    updatedAt = fixedInstant
                ),
                "namespace: test-ns\nid: workflow-1\nversion: 1"
            )

            val alreadyActiveRevision = WorkflowRevisionWithSource.fromRevision(
                WorkflowRevision(
                    namespace = "test-ns",
                    id = "workflow-1",
                    version = 1,
                    name = "Test Workflow",
                    description = "Test description",
                    steps = listOf(LogTask("Test message")),
                    active = true,
                    createdAt = fixedInstant,
                    updatedAt = fixedInstant
                ),
                "namespace: test-ns\nid: workflow-1\nversion: 1\nupdatedAt: ${fixedInstant}"
            )

            every { repository.findByIdWithSource(revisionId) } returns existingRevision
            every { repository.activateWithSource(revisionId, any()) } returns alreadyActiveRevision

            // When
            val result = useCase.execute("test-ns", "workflow-1", 1, fixedInstant)

            // Then
            result.active shouldBe true
            verify(exactly = 1) { repository.findByIdWithSource(revisionId) }
            verify(exactly = 1) { repository.activateWithSource(revisionId, any()) }
        }

        scenario("should support activating multiple versions of same workflow") {
            // Given
            val repository = mockk<IWorkflowRevisionRepository>()
            val useCase = ActivateRevisionUseCase(repository, fixedClock)

            val revision1Id = WorkflowRevisionID("test-ns", "workflow-1", 1)
            val revision2Id = WorkflowRevisionID("test-ns", "workflow-1", 2)

            val existing1 = WorkflowRevisionWithSource.fromRevision(
                WorkflowRevision(
                    namespace = "test-ns",
                    id = "workflow-1",
                    version = 1,
                    name = "Test Workflow",
                    description = "Version 1",
                    steps = listOf(LogTask("V1")),
                    active = false,
                    createdAt = fixedInstant,
                    updatedAt = fixedInstant
                ),
                "namespace: test-ns\nid: workflow-1\nversion: 1"
            )

            val existing2 = WorkflowRevisionWithSource.fromRevision(
                WorkflowRevision(
                    namespace = "test-ns",
                    id = "workflow-1",
                    version = 2,
                    name = "Test Workflow",
                    description = "Version 2",
                    steps = listOf(LogTask("V2")),
                    active = false,
                    createdAt = fixedInstant,
                    updatedAt = fixedInstant
                ),
                "namespace: test-ns\nid: workflow-1\nversion: 2"
            )

            val revision1 = WorkflowRevisionWithSource.fromRevision(
                WorkflowRevision(
                    namespace = "test-ns",
                    id = "workflow-1",
                    version = 1,
                    name = "Test Workflow",
                    description = "Version 1",
                    steps = listOf(LogTask("V1")),
                    active = true,
                    createdAt = fixedInstant,
                    updatedAt = fixedInstant
                ),
                "namespace: test-ns\nid: workflow-1\nversion: 1\nupdatedAt: ${fixedInstant}"
            )

            val revision2 = WorkflowRevisionWithSource.fromRevision(
                WorkflowRevision(
                    namespace = "test-ns",
                    id = "workflow-1",
                    version = 2,
                    name = "Test Workflow",
                    description = "Version 2",
                    steps = listOf(LogTask("V2")),
                    active = true,
                    createdAt = fixedInstant,
                    updatedAt = fixedInstant
                ),
                "namespace: test-ns\nid: workflow-1\nversion: 2\nupdatedAt: ${fixedInstant}"
            )

            every { repository.findByIdWithSource(revision1Id) } returns existing1
            every { repository.findByIdWithSource(revision2Id) } returns existing2
            every { repository.activateWithSource(revision1Id, any()) } returns revision1
            every { repository.activateWithSource(revision2Id, any()) } returns revision2

            // When
            val result1 = useCase.execute("test-ns", "workflow-1", 1, fixedInstant)
            val result2 = useCase.execute("test-ns", "workflow-1", 2, fixedInstant)

            // Then - both should be active (multi-active support)
            result1.active shouldBe true
            result1.version shouldBe 1
            result2.active shouldBe true
            result2.version shouldBe 2

            verify(exactly = 1) { repository.findByIdWithSource(revision1Id) }
            verify(exactly = 1) { repository.findByIdWithSource(revision2Id) }
            verify(exactly = 1) { repository.activateWithSource(revision1Id, any()) }
            verify(exactly = 1) { repository.activateWithSource(revision2Id, any()) }
        }
    }
})
