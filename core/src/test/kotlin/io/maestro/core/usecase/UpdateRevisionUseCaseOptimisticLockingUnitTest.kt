package io.maestro.core.usecase

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.maestro.core.IWorkflowRevisionRepository
import io.maestro.core.WorkflowYamlParser
import io.maestro.core.WorkflowYamlMetadataUpdater
import io.maestro.core.errors.OptimisticLockException
import io.maestro.core.steps.LogTask
import io.maestro.model.WorkflowRevision
import io.maestro.model.WorkflowRevisionID
import io.maestro.model.WorkflowRevisionWithSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * Unit tests for optimistic locking in UpdateRevisionUseCase.
 *
 * Tests T099 - Concurrent revision update conflict detection
 * using the updatedAt field from YAML.
 */
class UpdateRevisionUseCaseOptimisticLockingUnitTest : DescribeSpec({

    val fixedInstant = Instant.parse("2025-11-24T20:00:00Z")
    val clock = Clock.fixed(fixedInstant, ZoneId.of("UTC"))

    describe("UpdateRevisionUseCase optimistic locking") {

        it("should succeed when YAML updatedAt matches database updatedAt") {
            val repository = mockk<IWorkflowRevisionRepository>()
            val yamlParser = mockk<WorkflowYamlParser>()
            val useCase = UpdateRevisionUseCase(repository, yamlParser, clock)

            val currentUpdatedAt = Instant.parse("2025-11-24T19:30:00Z")
            val existingRevision = WorkflowRevision(
                namespace = "test",
                id = "workflow1",
                version = 1,
                name = "Original",
                description = "Original description",
                steps = listOf(LogTask("Original message")),
                active = false,
                createdAt = Instant.parse("2025-11-24T19:00:00Z"),
                updatedAt = currentUpdatedAt
            )

            val parsedRevision = existingRevision.copy(
                name = "Updated",
                description = "Updated description",
                updatedAt = currentUpdatedAt  // Matches database value
            )

            val yaml = """
                namespace: test
                id: workflow1
                version: 1
                name: Updated
                description: Updated description
                updatedAt: ${currentUpdatedAt}
                steps:
                  - type: LogTask
                    message: Updated message
            """.trimIndent()

            val updatedYaml = yaml.replace("updatedAt: ${currentUpdatedAt}", "updatedAt: $fixedInstant")

            mockkObject(WorkflowYamlMetadataUpdater)
            every {
                WorkflowYamlMetadataUpdater.updateAllMetadata(
                    any(), any(), any(), any()
                )
            } returns updatedYaml

            coEvery { repository.findById(WorkflowRevisionID("test", "workflow1", 1)) } returns existingRevision
            coEvery { yamlParser.parseRevision(yaml, true) } returns parsedRevision
            coEvery { repository.updateWithSource(any()) } returns WorkflowRevisionWithSource(
                parsedRevision.copy(updatedAt = fixedInstant),
                updatedYaml
            )

            // Act: Update with matching updatedAt in YAML
            val result = useCase.execute("test", "workflow1", 1, yaml)

            // Assert: Update succeeds
            result.revision.name shouldBe "Updated"
            coVerify(exactly = 1) { repository.updateWithSource(any()) }
        }

        it("should throw OptimisticLockException when YAML updatedAt does not match database updatedAt") {
            val repository = mockk<IWorkflowRevisionRepository>()
            val yamlParser = mockk<WorkflowYamlParser>()
            val useCase = UpdateRevisionUseCase(repository, yamlParser, clock)

            val currentUpdatedAt = Instant.parse("2025-11-24T19:45:00Z")  // Current value in DB
            val staleUpdatedAt = Instant.parse("2025-11-24T19:30:00Z")     // Stale value in YAML

            val existingRevision = WorkflowRevision(
                namespace = "test",
                id = "workflow1",
                version = 1,
                name = "Modified by another user",
                description = "Modified description",
                steps = listOf(LogTask("Modified message")),
                active = false,
                createdAt = Instant.parse("2025-11-24T19:00:00Z"),
                updatedAt = currentUpdatedAt  // Different from staleUpdatedAt
            )

            val parsedRevision = existingRevision.copy(
                name = "My Update",
                description = "My description",
                updatedAt = staleUpdatedAt  // Stale value from YAML
            )

            val yaml = """
                namespace: test
                id: workflow1
                version: 1
                name: My Update
                description: My description
                updatedAt: $staleUpdatedAt
                steps:
                  - type: LogTask
                    message: My message
            """.trimIndent()

            coEvery { repository.findById(WorkflowRevisionID("test", "workflow1", 1)) } returns existingRevision
            coEvery { yamlParser.parseRevision(yaml, true) } returns parsedRevision

            // Act & Assert: Update with stale updatedAt in YAML should throw OptimisticLockException
            val exception = shouldThrow<OptimisticLockException> {
                useCase.execute("test", "workflow1", 1, yaml)
            }

            // Verify exception details
            exception.revisionId shouldBe WorkflowRevisionID("test", "workflow1", 1)
            exception.expectedUpdatedAt shouldBe staleUpdatedAt
            exception.actualUpdatedAt shouldBe currentUpdatedAt
            exception.status shouldBe 409
            exception.title shouldBe "Optimistic Lock Conflict"
            exception.message shouldContain "has been modified by another user"
            exception.message shouldContain "Expected updatedAt: $staleUpdatedAt"
            exception.message shouldContain "Actual updatedAt: $currentUpdatedAt"

            // Verify update was NOT performed
            coVerify(exactly = 0) { repository.updateWithSource(any()) }
        }

        it("should detect concurrent updates in realistic scenario") {
            val repository = mockk<IWorkflowRevisionRepository>()
            val yamlParser = mockk<WorkflowYamlParser>()
            val useCase = UpdateRevisionUseCase(repository, yamlParser, clock)

            // Both users read the same revision at the same time
            val initialUpdatedAt = Instant.parse("2025-11-24T19:00:00Z")
            val revisionWhenBothUsersRead = WorkflowRevision(
                namespace = "prod",
                id = "payment-workflow",
                version = 1,
                name = "Payment Processing",
                description = "Initial version",
                steps = listOf(LogTask("Process payment")),
                active = false,
                createdAt = Instant.parse("2025-11-24T18:00:00Z"),
                updatedAt = initialUpdatedAt
            )

            // User A updates first - succeeds and changes updatedAt
            val userAUpdatedAt = Instant.parse("2025-11-24T19:15:00Z")
            val revisionAfterUserA = revisionWhenBothUsersRead.copy(
                description = "User A's changes",
                updatedAt = userAUpdatedAt
            )

            val userAYaml = """
                namespace: prod
                id: payment-workflow
                version: 1
                name: Payment Processing
                description: User A's changes
                updatedAt: $initialUpdatedAt
                steps:
                  - type: LogTask
                    message: Process payment with User A logic
            """.trimIndent()

            val userBYaml = """
                namespace: prod
                id: payment-workflow
                version: 1
                name: Payment Processing
                description: User B's changes
                updatedAt: $initialUpdatedAt
                steps:
                  - type: LogTask
                    message: Process payment with User B logic
            """.trimIndent()

            val updatedUserAYaml = userAYaml.replace("updatedAt: $initialUpdatedAt", "updatedAt: $fixedInstant")

            mockkObject(WorkflowYamlMetadataUpdater)
            every {
                WorkflowYamlMetadataUpdater.updateAllMetadata(any(), any(), any(), any())
            } returns updatedUserAYaml

            // User A's update (first)
            coEvery { repository.findById(WorkflowRevisionID("prod", "payment-workflow", 1)) } returns
                    revisionWhenBothUsersRead andThen revisionAfterUserA

            coEvery { yamlParser.parseRevision(userAYaml, true) } returns revisionWhenBothUsersRead.copy(
                description = "User A's changes",
                updatedAt = initialUpdatedAt  // YAML has initial timestamp
            )

            coEvery { yamlParser.parseRevision(userBYaml, true) } returns revisionWhenBothUsersRead.copy(
                description = "User B's changes",
                updatedAt = initialUpdatedAt  // YAML has stale timestamp
            )

            coEvery { repository.updateWithSource(any()) } returns
                    WorkflowRevisionWithSource(revisionAfterUserA, updatedUserAYaml)

            // User A updates successfully (YAML updatedAt matches database)
            val userAResult = useCase.execute("prod", "payment-workflow", 1, userAYaml)

            userAResult.revision.description shouldBe "User A's changes"

            // User B tries to update with stale timestamp in YAML (should fail)
            val exception = shouldThrow<OptimisticLockException> {
                useCase.execute("prod", "payment-workflow", 1, userBYaml)
            }

            exception.message shouldContain "has been modified by another user"
            exception.expectedUpdatedAt shouldBe initialUpdatedAt
            exception.actualUpdatedAt shouldBe userAUpdatedAt
        }
    }
})
