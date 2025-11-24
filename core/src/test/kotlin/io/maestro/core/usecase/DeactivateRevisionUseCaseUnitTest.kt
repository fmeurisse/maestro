package io.maestro.core.usecase

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.maestro.core.IWorkflowRevisionRepository
import io.maestro.core.WorkflowYamlMetadataUpdater
import io.maestro.core.errors.WorkflowRevisionNotFoundException
import io.maestro.core.steps.LogTask
import io.maestro.model.WorkflowRevision
import io.maestro.model.WorkflowRevisionID
import io.maestro.model.WorkflowRevisionWithSource
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class DeactivateRevisionUseCaseUnitTest : FeatureSpec({

    val fixedInstant = Instant.parse("2024-01-01T12:00:00Z")
    val fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    feature("Deactivate workflow revision") {

        scenario("should deactivate an active revision") {
            // Given
            val repository = mockk<IWorkflowRevisionRepository>()
            val useCase = DeactivateRevisionUseCase(repository, fixedClock)

            val revisionId = WorkflowRevisionID("test-ns", "workflow-1", 1)

            val originalYaml = "namespace: test-ns\nid: workflow-1\nversion: 1"
            val existingRevision = WorkflowRevisionWithSource.fromRevision(
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
                originalYaml
            )

            val expectedUpdatedYaml = WorkflowYamlMetadataUpdater.updateTimestamp(originalYaml, fixedInstant)
            val deactivatedRevision = WorkflowRevisionWithSource.fromRevision(
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
                expectedUpdatedYaml
            )

            val yamlSlot = slot<String>()
            every { repository.findByIdWithSource(revisionId) } returns existingRevision
            every { repository.deactivateWithSource(revisionId, capture(yamlSlot)) } returns deactivatedRevision

            // When
            val result = useCase.execute("test-ns", "workflow-1", 1)

            // Then
            result.active shouldBe false
            result.namespace shouldBe "test-ns"
            result.id shouldBe "workflow-1"
            result.version shouldBe 1

            // Verify the YAML passed to repository has updated timestamp
            yamlSlot.captured shouldBe expectedUpdatedYaml

            verify(exactly = 1) { repository.findByIdWithSource(revisionId) }
            verify(exactly = 1) { repository.deactivateWithSource(revisionId, any()) }
        }

        scenario("should deactivate using WorkflowRevisionID") {
            // Given
            val repository = mockk<IWorkflowRevisionRepository>()
            val useCase = DeactivateRevisionUseCase(repository, fixedClock)

            val revisionId = WorkflowRevisionID("test-ns", "workflow-1", 2)

            val originalYaml = "namespace: test-ns\nid: workflow-1\nversion: 2"
            val existingRevision = WorkflowRevisionWithSource.fromRevision(
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
                originalYaml
            )

            val expectedUpdatedYaml = WorkflowYamlMetadataUpdater.updateTimestamp(originalYaml, fixedInstant)
            val deactivatedRevision = WorkflowRevisionWithSource.fromRevision(
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
                expectedUpdatedYaml
            )

            val yamlSlot = slot<String>()
            every { repository.findByIdWithSource(revisionId) } returns existingRevision
            every { repository.deactivateWithSource(revisionId, capture(yamlSlot)) } returns deactivatedRevision

            // When
            val result = useCase.execute(revisionId)

            // Then
            result.active shouldBe false
            yamlSlot.captured shouldBe expectedUpdatedYaml

            verify(exactly = 1) { repository.findByIdWithSource(revisionId) }
            verify(exactly = 1) { repository.deactivateWithSource(revisionId, any()) }
        }

        scenario("should throw exception when revision doesn't exist") {
            // Given
            val repository = mockk<IWorkflowRevisionRepository>()
            val useCase = DeactivateRevisionUseCase(repository, fixedClock)

            val revisionId = WorkflowRevisionID("test-ns", "non-existent", 1)

            every { repository.findByIdWithSource(revisionId) } returns null

            // When/Then
            shouldThrow<WorkflowRevisionNotFoundException> {
                useCase.execute("test-ns", "non-existent", 1)
            }

            verify(exactly = 1) { repository.findByIdWithSource(revisionId) }
            verify(exactly = 0) { repository.deactivateWithSource(any(), any()) }
        }

        scenario("should allow deactivating an already inactive revision (idempotent)") {
            // Given
            val repository = mockk<IWorkflowRevisionRepository>()
            val useCase = DeactivateRevisionUseCase(repository, fixedClock)

            val revisionId = WorkflowRevisionID("test-ns", "workflow-1", 1)

            val originalYaml = "namespace: test-ns\nid: workflow-1\nversion: 1"
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
                originalYaml
            )

            val expectedUpdatedYaml = WorkflowYamlMetadataUpdater.updateTimestamp(originalYaml, fixedInstant)
            val alreadyInactiveRevision = WorkflowRevisionWithSource.fromRevision(
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
                expectedUpdatedYaml
            )

            val yamlSlot = slot<String>()
            every { repository.findByIdWithSource(revisionId) } returns existingRevision
            every { repository.deactivateWithSource(revisionId, capture(yamlSlot)) } returns alreadyInactiveRevision

            // When
            val result = useCase.execute("test-ns", "workflow-1", 1)

            // Then
            result.active shouldBe false
            yamlSlot.captured shouldBe expectedUpdatedYaml

            verify(exactly = 1) { repository.findByIdWithSource(revisionId) }
            verify(exactly = 1) { repository.deactivateWithSource(revisionId, any()) }
        }

        scenario("should support deactivating one version while others remain active") {
            // Given
            val repository = mockk<IWorkflowRevisionRepository>()
            val useCase = DeactivateRevisionUseCase(repository, fixedClock)

            val revision1Id = WorkflowRevisionID("test-ns", "workflow-1", 1)

            val originalYaml = "namespace: test-ns\nid: workflow-1\nversion: 1"
            val existingRevision = WorkflowRevisionWithSource.fromRevision(
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
                originalYaml
            )

            val expectedUpdatedYaml = WorkflowYamlMetadataUpdater.updateTimestamp(originalYaml, fixedInstant)
            val deactivatedRevision1 = WorkflowRevisionWithSource.fromRevision(
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
                expectedUpdatedYaml
            )

            val yamlSlot = slot<String>()
            every { repository.findByIdWithSource(revision1Id) } returns existingRevision
            every { repository.deactivateWithSource(revision1Id, capture(yamlSlot)) } returns deactivatedRevision1

            // When
            val result = useCase.execute("test-ns", "workflow-1", 1)

            // Then - version 1 is now inactive
            result.active shouldBe false
            result.version shouldBe 1
            yamlSlot.captured shouldBe expectedUpdatedYaml

            verify(exactly = 1) { repository.findByIdWithSource(revision1Id) }
            verify(exactly = 1) { repository.deactivateWithSource(revision1Id, any()) }
        }
    }
})
