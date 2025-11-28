package io.maestro.core.executions.usecases

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.maestro.core.executions.IWorkflowExecutionRepository
import io.maestro.model.WorkflowRevisionID
import io.maestro.model.execution.ExecutionStatus
import io.maestro.model.execution.WorkflowExecution
import io.maestro.model.execution.WorkflowExecutionID
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class GetExecutionHistoryUseCaseUnitTest {

    private lateinit var executionRepository: IWorkflowExecutionRepository
    private lateinit var useCase: GetExecutionHistoryUseCase

    @BeforeEach
    fun setup() {
        executionRepository = mockk()
        useCase = GetExecutionHistoryUseCase(executionRepository)
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `getHistory should return executions for workflow`() {
        // Given: A workflow with executions
        val namespace = "test-ns"
        val workflowId = "test-workflow"
        val revisionId = WorkflowRevisionID(namespace, workflowId, 1)
        val execution1 = createExecution(WorkflowExecutionID.generate(), revisionId, ExecutionStatus.COMPLETED)
        val execution2 = createExecution(WorkflowExecutionID.generate(), revisionId, ExecutionStatus.COMPLETED)
        val executions = listOf(execution1, execution2)

        // Mock: Repository returns executions
        every { executionRepository.findByWorkflow(namespace, workflowId, null, null, 20, 0) } returns executions
        every { executionRepository.countByWorkflow(namespace, workflowId, null, null) } returns 2L

        // When: Getting history
        val result = useCase.getHistory(namespace, workflowId)

        // Then: Should return executions
        result.executions shouldNotBe null
        result.executions.size shouldBe 2
        result.executions shouldBe executions
        result.totalCount shouldBe 2L

        // Verify: Repository was called
        verify(exactly = 1) { executionRepository.findByWorkflow(namespace, workflowId, null, null, 20, 0) }
        verify(exactly = 1) { executionRepository.countByWorkflow(namespace, workflowId, null, null) }
    }

    @Test
    fun `getHistory should filter by status when provided`() {
        // Given: A workflow with executions of different statuses
        val namespace = "test-ns"
        val workflowId = "test-workflow"
        val revisionId = WorkflowRevisionID(namespace, workflowId, 1)
        val failedExecution = createExecution(WorkflowExecutionID.generate(), revisionId, ExecutionStatus.FAILED)
        val failedExecutions = listOf(failedExecution)

        // Mock: Repository returns only failed executions
        every { executionRepository.findByWorkflow(namespace, workflowId, null, ExecutionStatus.FAILED, 20, 0) } returns failedExecutions
        every { executionRepository.countByWorkflow(namespace, workflowId, null, ExecutionStatus.FAILED) } returns 1L

        // When: Getting history filtered by FAILED status
        val result = useCase.getHistory(namespace, workflowId, status = ExecutionStatus.FAILED)

        // Then: Should return only failed executions
        result.executions.size shouldBe 1
        result.executions[0].status shouldBe ExecutionStatus.FAILED
        result.totalCount shouldBe 1L

        // Verify: Repository was called with status filter
        verify(exactly = 1) { executionRepository.findByWorkflow(namespace, workflowId, null, ExecutionStatus.FAILED, 20, 0) }
        verify(exactly = 1) { executionRepository.countByWorkflow(namespace, workflowId, null, ExecutionStatus.FAILED) }
    }

    @Test
    fun `getHistory should support pagination with limit and offset`() {
        // Given: A workflow with many executions
        val namespace = "test-ns"
        val workflowId = "test-workflow"
        val revisionId = WorkflowRevisionID(namespace, workflowId, 1)
        val execution1 = createExecution(WorkflowExecutionID.generate(), revisionId, ExecutionStatus.COMPLETED)
        val paginatedExecutions = listOf(execution1)

        // Mock: Repository returns paginated results
        every { executionRepository.findByWorkflow(namespace, workflowId, null, null, 10, 5) } returns paginatedExecutions
        every { executionRepository.countByWorkflow(namespace, workflowId, null, null) } returns 15L

        // When: Getting history with pagination
        val result = useCase.getHistory(namespace, workflowId, limit = 10, offset = 5)

        // Then: Should return paginated results
        result.executions.size shouldBe 1
        result.totalCount shouldBe 15L

        // Verify: Repository was called with pagination parameters
        verify(exactly = 1) { executionRepository.findByWorkflow(namespace, workflowId, null, null, 10, 5) }
        verify(exactly = 1) { executionRepository.countByWorkflow(namespace, workflowId, null, null) }
    }

    @Test
    fun `getHistory should return empty list when no executions exist`() {
        // Given: A workflow with no executions
        val namespace = "test-ns"
        val workflowId = "test-workflow"

        // Mock: Repository returns empty list
        every { executionRepository.findByWorkflow(namespace, workflowId, null, null, 20, 0) } returns emptyList()
        every { executionRepository.countByWorkflow(namespace, workflowId, null, null) } returns 0L

        // When: Getting history
        val result = useCase.getHistory(namespace, workflowId)

        // Then: Should return empty list
        result.executions shouldBe emptyList()
        result.totalCount shouldBe 0L

        // Verify: Repository was called
        verify(exactly = 1) { executionRepository.findByWorkflow(namespace, workflowId, null, null, 20, 0) }
        verify(exactly = 1) { executionRepository.countByWorkflow(namespace, workflowId, null, null) }
    }

    @Test
    fun `getHistory should use default pagination when not specified`() {
        // Given: A workflow
        val namespace = "test-ns"
        val workflowId = "test-workflow"
        val revisionId = WorkflowRevisionID(namespace, workflowId, 1)
        val executions = listOf(createExecution(WorkflowExecutionID.generate(), revisionId, ExecutionStatus.COMPLETED))

        // Mock: Repository returns results with default pagination
        every { executionRepository.findByWorkflow(namespace, workflowId, null, null, 20, 0) } returns executions
        every { executionRepository.countByWorkflow(namespace, workflowId, null, null) } returns 1L

        // When: Getting history without pagination parameters
        val result = useCase.getHistory(namespace, workflowId)

        // Then: Should use default pagination (limit=20, offset=0)
        result.executions.size shouldBe 1

        // Verify: Repository was called with default pagination
        verify(exactly = 1) { executionRepository.findByWorkflow(namespace, workflowId, null, null, 20, 0) }
    }

    @Test
    fun `getHistory should return executions sorted by most recent first`() {
        // Given: A workflow with executions at different times
        val namespace = "test-ns"
        val workflowId = "test-workflow"
        val revisionId = WorkflowRevisionID(namespace, workflowId, 1)
        val now = Instant.now()
        val execution1 = createExecution(
            WorkflowExecutionID.generate(),
            revisionId,
            ExecutionStatus.COMPLETED,
            startedAt = now.minusSeconds(100)
        )
        val execution2 = createExecution(
            WorkflowExecutionID.generate(),
            revisionId,
            ExecutionStatus.COMPLETED,
            startedAt = now.minusSeconds(50)
        )
        val execution3 = createExecution(
            WorkflowExecutionID.generate(),
            revisionId,
            ExecutionStatus.COMPLETED,
            startedAt = now.minusSeconds(10)
        )
        // Repository should return them sorted descending (most recent first)
        val executions = listOf(execution3, execution2, execution1)

        // Mock: Repository returns sorted executions
        every { executionRepository.findByWorkflow(namespace, workflowId, null, null, 20, 0) } returns executions
        every { executionRepository.countByWorkflow(namespace, workflowId, null, null) } returns 3L

        // When: Getting history
        val result = useCase.getHistory(namespace, workflowId)

        // Then: Executions should be sorted by most recent first
        result.executions.size shouldBe 3
        result.executions[0].startedAt shouldBe now.minusSeconds(10)
        result.executions[1].startedAt shouldBe now.minusSeconds(50)
        result.executions[2].startedAt shouldBe now.minusSeconds(100)

        // Verify: Repository was called
        verify(exactly = 1) { executionRepository.findByWorkflow(namespace, workflowId, null, null, 20, 0) }
    }

    @Test
    fun `getHistory should handle multiple status filters`() {
        // Given: A workflow
        val namespace = "test-ns"
        val workflowId = "test-workflow"
        val revisionId = WorkflowRevisionID(namespace, workflowId, 1)
        val completedExecution = createExecution(WorkflowExecutionID.generate(), revisionId, ExecutionStatus.COMPLETED)
        val runningExecution = createExecution(WorkflowExecutionID.generate(), revisionId, ExecutionStatus.RUNNING)

        // Test COMPLETED filter
        every { executionRepository.findByWorkflow(namespace, workflowId, null, ExecutionStatus.COMPLETED, 20, 0) } returns listOf(completedExecution)
        every { executionRepository.countByWorkflow(namespace, workflowId, null, ExecutionStatus.COMPLETED) } returns 1L

        val completedResult = useCase.getHistory(namespace, workflowId, status = ExecutionStatus.COMPLETED)
        completedResult.executions.size shouldBe 1
        completedResult.executions[0].status shouldBe ExecutionStatus.COMPLETED

        // Test RUNNING filter
        every { executionRepository.findByWorkflow(namespace, workflowId, null, ExecutionStatus.RUNNING, 20, 0) } returns listOf(runningExecution)
        every { executionRepository.countByWorkflow(namespace, workflowId, null, ExecutionStatus.RUNNING) } returns 1L

        val runningResult = useCase.getHistory(namespace, workflowId, status = ExecutionStatus.RUNNING)
        runningResult.executions.size shouldBe 1
        runningResult.executions[0].status shouldBe ExecutionStatus.RUNNING
    }

    @Test
    fun `getHistory should filter by version when provided`() {
        // Given: A workflow with executions in different versions
        val namespace = "test-ns"
        val workflowId = "test-workflow"
        val revisionId1 = WorkflowRevisionID(namespace, workflowId, 1)
        val revisionId2 = WorkflowRevisionID(namespace, workflowId, 2)
        val execution1 = createExecution(WorkflowExecutionID.generate(), revisionId1, ExecutionStatus.COMPLETED)
        val execution2 = createExecution(WorkflowExecutionID.generate(), revisionId2, ExecutionStatus.COMPLETED)

        // Test version 1 filter
        every { executionRepository.findByWorkflow(namespace, workflowId, 1, null, 20, 0) } returns listOf(execution1)
        every { executionRepository.countByWorkflow(namespace, workflowId, 1, null) } returns 1L

        val version1Result = useCase.getHistory(namespace, workflowId, version = 1)
        version1Result.executions.size shouldBe 1
        version1Result.executions[0].revisionId.version shouldBe 1

        // Test version 2 filter
        every { executionRepository.findByWorkflow(namespace, workflowId, 2, null, 20, 0) } returns listOf(execution2)
        every { executionRepository.countByWorkflow(namespace, workflowId, 2, null) } returns 1L

        val version2Result = useCase.getHistory(namespace, workflowId, version = 2)
        version2Result.executions.size shouldBe 1
        version2Result.executions[0].revisionId.version shouldBe 2
    }

    private fun createExecution(
        executionId: WorkflowExecutionID,
        revisionId: WorkflowRevisionID,
        status: ExecutionStatus,
        startedAt: Instant = Instant.now().minusSeconds(60)
    ): WorkflowExecution {
        return WorkflowExecution(
            executionId = executionId,
            revisionId = revisionId,
            inputParameters = mapOf("test" to "value"),
            status = status,
            errorMessage = if (status == ExecutionStatus.FAILED) "Test error" else null,
            startedAt = startedAt,
            completedAt = if (status in listOf(ExecutionStatus.COMPLETED, ExecutionStatus.FAILED)) startedAt.plusSeconds(10) else null,
            lastUpdatedAt = Instant.now()
        )
    }
}
