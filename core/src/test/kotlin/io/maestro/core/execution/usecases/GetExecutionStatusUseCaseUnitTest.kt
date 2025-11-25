package io.maestro.core.execution.usecases

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.maestro.core.execution.repository.IWorkflowExecutionRepository
import io.maestro.model.WorkflowRevisionID
import io.maestro.model.execution.ExecutionStatus
import io.maestro.model.execution.StepStatus
import io.maestro.model.execution.WorkflowExecution
import io.maestro.model.execution.WorkflowExecutionID
import io.maestro.model.execution.ExecutionStepResult
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class GetExecutionStatusUseCaseUnitTest {

    private lateinit var executionRepository: IWorkflowExecutionRepository
    private lateinit var useCase: GetExecutionStatusUseCase

    @BeforeEach
    fun setup() {
        executionRepository = mockk()
        useCase = GetExecutionStatusUseCase(executionRepository)
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `getStatus should return execution when found`() {
        // Given: An execution exists
        val executionId = WorkflowExecutionID.generate()
        val revisionId = WorkflowRevisionID("test-ns", "test-workflow", 1)
        val execution = WorkflowExecution(
            executionId = executionId,
            revisionId = revisionId,
            inputParameters = mapOf("userId" to "user123"),
            status = ExecutionStatus.COMPLETED,
            errorMessage = null,
            startedAt = Instant.now().minusSeconds(60),
            completedAt = Instant.now(),
            lastUpdatedAt = Instant.now()
        )

        // Mock: Repository returns the execution
        every { executionRepository.findById(executionId) } returns execution

        // When: Getting status
        val result = useCase.getStatus(executionId)

        // Then: Should return the execution
        result shouldNotBe null
        result shouldBe execution
        result?.executionId shouldBe executionId
        result?.status shouldBe ExecutionStatus.COMPLETED

        // Verify: Repository was called
        verify(exactly = 1) { executionRepository.findById(executionId) }
    }

    @Test
    fun `getStatus should return null when execution not found`() {
        // Given: An execution does not exist
        val executionId = WorkflowExecutionID.generate()

        // Mock: Repository returns null
        every { executionRepository.findById(executionId) } returns null

        // When: Getting status
        val result = useCase.getStatus(executionId)

        // Then: Should return null
        result shouldBe null

        // Verify: Repository was called
        verify(exactly = 1) { executionRepository.findById(executionId) }
    }

    @Test
    fun `getStatus should return completed execution`() {
        // Given: A completed execution
        val executionId = WorkflowExecutionID.generate()
        val revisionId = WorkflowRevisionID("test-ns", "test-workflow", 1)

        val execution = WorkflowExecution(
            executionId = executionId,
            revisionId = revisionId,
            inputParameters = mapOf("userId" to "user123"),
            status = ExecutionStatus.COMPLETED,
            errorMessage = null,
            startedAt = Instant.now().minusSeconds(60),
            completedAt = Instant.now().minusSeconds(40),
            lastUpdatedAt = Instant.now().minusSeconds(40)
        )

        // Mock: Repository returns the completed execution
        every { executionRepository.findById(executionId) } returns execution

        // When: Getting status
        val result = useCase.getStatus(executionId)

        // Then: Should return completed execution
        result shouldNotBe null
        result?.status shouldBe ExecutionStatus.COMPLETED
        result?.completedAt shouldNotBe null

        // Verify: Repository was called
        verify(exactly = 1) { executionRepository.findById(executionId) }
    }

    @Test
    fun `getStatus should return execution with FAILED status and error message`() {
        // Given: A failed execution
        val executionId = WorkflowExecutionID.generate()
        val revisionId = WorkflowRevisionID("test-ns", "test-workflow", 1)

        val execution = WorkflowExecution(
            executionId = executionId,
            revisionId = revisionId,
            inputParameters = mapOf("userId" to "user123"),
            status = ExecutionStatus.FAILED,
            errorMessage = "Step execution failed: NullPointerException",
            startedAt = Instant.now().minusSeconds(60),
            completedAt = Instant.now(),
            lastUpdatedAt = Instant.now()
        )

        // Mock: Repository returns the failed execution
        every { executionRepository.findById(executionId) } returns execution

        // When: Getting status
        val result = useCase.getStatus(executionId)

        // Then: Should return execution with failure details
        result shouldNotBe null
        result?.status shouldBe ExecutionStatus.FAILED
        result?.errorMessage shouldNotBe null
        result?.errorMessage shouldBe "Step execution failed: NullPointerException"

        // Verify: Repository was called
        verify(exactly = 1) { executionRepository.findById(executionId) }
    }

    @Test
    fun `getStatus should return execution with RUNNING status`() {
        // Given: An execution in progress
        val executionId = WorkflowExecutionID.generate()
        val revisionId = WorkflowRevisionID("test-ns", "test-workflow", 1)

        val execution = WorkflowExecution(
            executionId = executionId,
            revisionId = revisionId,
            inputParameters = mapOf("userId" to "user123"),
            status = ExecutionStatus.RUNNING,
            errorMessage = null,
            startedAt = Instant.now().minusSeconds(10),
            completedAt = null, // Still running
            lastUpdatedAt = Instant.now()
        )

        // Mock: Repository returns the running execution
        every { executionRepository.findById(executionId) } returns execution

        // When: Getting status
        val result = useCase.getStatus(executionId)

        // Then: Should return execution with RUNNING status
        result shouldNotBe null
        result?.status shouldBe ExecutionStatus.RUNNING
        result?.completedAt shouldBe null

        // Verify: Repository was called
        verify(exactly = 1) { executionRepository.findById(executionId) }
    }

    @Test
    fun `getStatus should preserve input parameters in returned execution`() {
        // Given: An execution with specific input parameters
        val executionId = WorkflowExecutionID.generate()
        val revisionId = WorkflowRevisionID("test-ns", "test-workflow", 1)
        val inputParams = mapOf(
            "userId" to "user123",
            "retryCount" to 5,
            "enableDebug" to true
        )

        val execution = WorkflowExecution(
            executionId = executionId,
            revisionId = revisionId,
            inputParameters = inputParams,
            status = ExecutionStatus.COMPLETED,
            errorMessage = null,
            startedAt = Instant.now().minusSeconds(60),
            completedAt = Instant.now(),
            lastUpdatedAt = Instant.now()
        )

        // Mock: Repository returns the execution
        every { executionRepository.findById(executionId) } returns execution

        // When: Getting status
        val result = useCase.getStatus(executionId)

        // Then: Input parameters should be preserved
        result shouldNotBe null
        result?.inputParameters shouldBe inputParams
        result?.inputParameters?.get("userId") shouldBe "user123"
        result?.inputParameters?.get("retryCount") shouldBe 5
        result?.inputParameters?.get("enableDebug") shouldBe true

        // Verify: Repository was called
        verify(exactly = 1) { executionRepository.findById(executionId) }
    }
}
