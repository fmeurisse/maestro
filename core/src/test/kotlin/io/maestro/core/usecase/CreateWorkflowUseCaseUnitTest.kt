package io.maestro.core.usecase

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.maestro.core.IWorkflowRevisionRepository
import io.maestro.core.WorkflowYamlParser
import io.maestro.core.exception.WorkflowAlreadyExistsException
import io.maestro.core.exception.WorkflowRevisionParsingException
import io.maestro.core.steps.LogTask
import io.maestro.model.WorkflowID
import io.maestro.model.WorkflowRevision
import io.maestro.model.WorkflowRevisionID
import io.maestro.model.WorkflowRevisionWithSource
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Unit tests for CreateWorkflowUseCase.
 *
 * Tests verify:
 * - Successful workflow creation with proper defaults (version 1, timestamps, active=false)
 * - Workflow uniqueness validation (REQ-WF-004)
 * - YAML parsing and validation (REQ-WF-006)
 * - Error handling for various failure scenarios
 * - Proper interaction with repository and parser
 */
class CreateWorkflowUseCaseUnitTest : FeatureSpec({

    val repository = mockk<IWorkflowRevisionRepository>()
    val yamlParser = WorkflowYamlParser()
    val fixedClock = Clock.fixed(Instant.now(), ZoneOffset.UTC)
    val useCase = CreateWorkflowUseCase(repository, yamlParser, fixedClock)
    
    beforeEach {
        clearMocks(repository)
    }

    feature("successful workflow creation") {
        scenario("should create workflow with version 1 and default values") {
            // Given
            val yaml = """
                namespace: test-ns
                id: workflow-1
                version: 0
                name: Test Workflow
                description: A test workflow
                steps:
                  - type: LogTask
                    message: Hello, World!
                active: false
            """.trimIndent()

            val workflowId = WorkflowID("test-ns", "workflow-1")
            val expectedTimestamp = Instant.now(fixedClock)
            var capturedRevision: WorkflowRevisionWithSource? = null

            every { repository.exists(workflowId) } returns false
            every { repository.saveWithSource(any()) } answers {
                capturedRevision = args[0] as WorkflowRevisionWithSource
                WorkflowRevisionWithSource.fromRevision(capturedRevision.revision, yaml)
            }

            // When
            val result = useCase.execute(yaml)

            // Then
            result shouldBe WorkflowRevisionID("test-ns", "workflow-1", 1)
            
            // Verify timestamps are set to the fixed Clock time when passed to saveWithSource
            capturedRevision.shouldNotBeNull()
            capturedRevision.namespace shouldBe "test-ns"
            capturedRevision.id shouldBe "workflow-1"
            capturedRevision.version shouldBe 1
            capturedRevision.steps shouldHaveSize 1
            capturedRevision.createdAt.shouldNotBeNull()
            capturedRevision.updatedAt.shouldNotBeNull()
            capturedRevision.createdAt shouldBe capturedRevision.updatedAt // Should be equal for new revision
            capturedRevision.createdAt shouldBe expectedTimestamp
            capturedRevision.updatedAt shouldBe expectedTimestamp

            verifyOrder {
                repository.exists(workflowId)
                repository.saveWithSource(any())
            }
        }

        scenario("should set version to 1 regardless of YAML version") {
            // Given - YAML has version 5, but use case should force version 1
            val yaml = """
                namespace: test-ns
                id: workflow-1
                version: 5
                name: Test Workflow
                description: A test workflow
                steps:
                  - type: LogTask
                    message: Test
                active: false
            """.trimIndent()

            val workflowId = WorkflowID("test-ns", "workflow-1")
            val expectedTimestamp = Instant.now(fixedClock)
            var capturedRevision: WorkflowRevisionWithSource? = null

            every { repository.exists(workflowId) } returns false
            every { repository.saveWithSource(any()) } answers {
                capturedRevision = args[0] as WorkflowRevisionWithSource
                WorkflowRevisionWithSource.fromRevision(capturedRevision.revision, yaml)
            }

            // When
            val result = useCase.execute(yaml)

            // Then
            result shouldBe WorkflowRevisionID("test-ns", "workflow-1", 1)
            
            // Verify saved revision
            capturedRevision.shouldNotBeNull()
            capturedRevision.namespace shouldBe "test-ns"
            capturedRevision.id shouldBe "workflow-1"
            capturedRevision.version shouldBe 1 // Use case forces version 1
            capturedRevision.createdAt shouldBe expectedTimestamp
            capturedRevision.updatedAt shouldBe expectedTimestamp
            capturedRevision.createdAt shouldBe capturedRevision.updatedAt
            
            verify { repository.saveWithSource(any()) }
        }

        scenario("should set timestamps when creating revision") {
            // Given
            val yaml = """
                namespace: test-ns
                id: workflow-1
                name: Test Workflow
                description: A test workflow
                steps:
                  - type: LogTask
                    message: Test
                active: false
            """.trimIndent()

            val workflowId = WorkflowID("test-ns", "workflow-1")
            val expectedTimestamp = Instant.now(fixedClock)
            var capturedRevision: WorkflowRevisionWithSource? = null

            every { repository.exists(workflowId) } returns false
            every { repository.saveWithSource(any()) } answers {
                capturedRevision = args[0] as WorkflowRevisionWithSource
                WorkflowRevisionWithSource.fromRevision(capturedRevision.revision, yaml)
            }

            // When
            val result = useCase.execute(yaml)

            // Then - Verify that timestamps are set to fixed Clock time
            result shouldBe WorkflowRevisionID("test-ns", "workflow-1", 1)
            
            capturedRevision.shouldNotBeNull()
            capturedRevision.createdAt shouldBe expectedTimestamp
            capturedRevision.updatedAt shouldBe expectedTimestamp
            capturedRevision.createdAt shouldBe capturedRevision.updatedAt
            
            verify { repository.saveWithSource(any()) }
        }

        scenario("should preserve active flag from parsed data") {
            // Given - YAML has active: true
            val yaml = """
                namespace: test-ns
                id: workflow-1
                name: Test Workflow
                description: A test workflow
                steps:
                  - type: LogTask
                    message: Test
                active: true
            """.trimIndent()

            val workflowId = WorkflowID("test-ns", "workflow-1")
            val expectedTimestamp = Instant.now(fixedClock)
            var capturedRevision: WorkflowRevisionWithSource? = null

            every { repository.exists(workflowId) } returns false
            every { repository.saveWithSource(any()) } answers {
                capturedRevision = args[0] as WorkflowRevisionWithSource
                WorkflowRevisionWithSource.fromRevision(capturedRevision.revision, yaml)
            }

            // When
            val result = useCase.execute(yaml)

            // Then
            result shouldBe WorkflowRevisionID("test-ns", "workflow-1", 1)
            
            capturedRevision.shouldNotBeNull()
            capturedRevision.active shouldBe true // Active flag preserved from YAML
            capturedRevision.createdAt shouldBe expectedTimestamp
            capturedRevision.updatedAt shouldBe expectedTimestamp
            capturedRevision.createdAt shouldBe capturedRevision.updatedAt
            
            verify { repository.saveWithSource(any()) }
        }
    }

    feature("workflow uniqueness validation") {
        scenario("should throw WorkflowAlreadyExistsException when workflow exists") {
            // Given
            val yaml = """
                namespace: test-ns
                id: workflow-1
                name: Test Workflow
                description: A test workflow
                steps:
                  - type: LogTask
                    message: Test
                active: false
            """.trimIndent()

            val workflowId = WorkflowID("test-ns", "workflow-1")

            every { repository.exists(workflowId) } returns true // Workflow already exists

            // When/Then
            val exception = shouldThrow<WorkflowAlreadyExistsException> {
                useCase.execute(yaml)
            }

            exception.message.shouldNotBeNull()
            exception.message shouldContain "Workflow"
            exception.message shouldContain workflowId.toString()
            
            verify(exactly = 0) { repository.saveWithSource(any()) }
        }

        scenario("should check workflow existence before saving") {
            // Given
            val yaml = """
                namespace: test-ns
                id: workflow-1
                name: Test Workflow
                description: A test workflow
                steps:
                  - type: LogTask
                    message: Test
                active: false
            """.trimIndent()

            val workflowId = WorkflowID("test-ns", "workflow-1")
            val expectedTimestamp = Instant.now(fixedClock)
            var capturedRevision: WorkflowRevisionWithSource? = null

            every { repository.exists(workflowId) } returns false
            every { repository.saveWithSource(any()) } answers {
                capturedRevision = args[0] as WorkflowRevisionWithSource
                WorkflowRevisionWithSource.fromRevision(capturedRevision.revision, yaml)
            }

            // When
            val result = useCase.execute(yaml)

            // Then - Verify order: exists -> save (parsing happens before exists check)
            result shouldBe WorkflowRevisionID("test-ns", "workflow-1", 1)
            
            capturedRevision.shouldNotBeNull()
            capturedRevision.createdAt shouldBe expectedTimestamp
            capturedRevision.updatedAt shouldBe expectedTimestamp
            capturedRevision.createdAt shouldBe capturedRevision.updatedAt
            
            verifyOrder {
                repository.exists(workflowId)
                repository.saveWithSource(any())
            }
        }
    }

    feature("YAML parsing and validation") {
        scenario("should throw WorkflowRevisionParsingException when YAML parsing fails") {
            // Given
            val invalidYaml = "invalid: yaml: [unclosed"

            // When/Then - Real parser will throw exception on invalid YAML
            val exception = shouldThrow<WorkflowRevisionParsingException> {
                useCase.execute(invalidYaml)
            }

            exception.message.shouldNotBeNull()
            exception.message shouldContain "Invalid YAML"
            
            verify(exactly = 0) { repository.exists(any()) }
            verify(exactly = 0) { repository.saveWithSource(any()) }
        }

    }

    feature("repository interaction") {
        scenario("should save revision with YAML source") {
            // Given
            val yaml = """
                namespace: test-ns
                id: workflow-1
                name: Test Workflow
                description: A test workflow
                steps:
                  - type: LogTask
                    message: Test
                active: false
            """.trimIndent()

            val workflowId = WorkflowID("test-ns", "workflow-1")
            val expectedTimestamp = Instant.now(fixedClock)
            var capturedRevision: WorkflowRevisionWithSource? = null

            every { repository.exists(workflowId) } returns false
            every { repository.saveWithSource(any()) } answers {
                capturedRevision = args[0] as WorkflowRevisionWithSource
                WorkflowRevisionWithSource.fromRevision(capturedRevision.revision, yaml)
            }

            // When
            val result = useCase.execute(yaml)

            // Then
            result shouldBe WorkflowRevisionID("test-ns", "workflow-1", 1)
            
            capturedRevision.shouldNotBeNull()
            capturedRevision.namespace shouldBe "test-ns"
            capturedRevision.id shouldBe "workflow-1"
            capturedRevision.version shouldBe 1
            capturedRevision.yamlSource shouldBe yaml // Verify YAML source is preserved
            capturedRevision.createdAt shouldBe expectedTimestamp
            capturedRevision.updatedAt shouldBe expectedTimestamp
            capturedRevision.createdAt shouldBe capturedRevision.updatedAt
            
            verify { repository.saveWithSource(any()) }
        }

        scenario("should propagate repository save errors") {
            // Given
            val yaml = """
                namespace: test-ns
                id: workflow-1
                name: Test Workflow
                description: A test workflow
                steps:
                  - type: LogTask
                    message: Test
                active: false
            """.trimIndent()

            val workflowId = WorkflowID("test-ns", "workflow-1")
            val repositoryError = RuntimeException("Database connection failed")

            every { repository.exists(workflowId) } returns false
            every { repository.saveWithSource(any()) } throws repositoryError

            // When/Then
            val exception = shouldThrow<RuntimeException> {
                useCase.execute(yaml)
            }

            exception shouldBe repositoryError
        }
    }
})
