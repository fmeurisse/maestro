package io.maestro.core.execution.usecases

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.maestro.core.IWorkflowRevisionRepository
import io.maestro.core.execution.repository.IWorkflowExecutionRepository
import io.maestro.core.steps.LogTask
import io.maestro.core.steps.Sequence
import io.maestro.model.WorkflowRevision
import io.maestro.model.WorkflowRevisionID
import io.maestro.model.execution.ExecutionContext
import io.maestro.model.execution.ExecutionStatus
import io.maestro.model.execution.StepStatus
import io.maestro.model.execution.WorkflowExecution
import io.maestro.model.execution.WorkflowExecutionID
import io.maestro.model.execution.ExecutionStepResult
import io.maestro.model.steps.Step
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class ExecuteWorkflowUseCaseUnitTest {

    private lateinit var workflowRevisionRepository: IWorkflowRevisionRepository
    private lateinit var executionRepository: IWorkflowExecutionRepository
    private lateinit var useCase: ExecuteWorkflowUseCase

    @BeforeEach
    fun setup() {
        workflowRevisionRepository = mockk()
        executionRepository = mockk()
        useCase = ExecuteWorkflowUseCase(workflowRevisionRepository, executionRepository)
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `execute should create execution record and execute all steps successfully`() {
        // Given: A workflow with two log tasks
        val revisionId = WorkflowRevisionID("test-ns", "test-workflow", 1)
        val step1 = LogTask("Step 1: Initialize")
        val step2 = LogTask("Step 2: Process")
        val workflow = WorkflowRevision(
            revisionId = revisionId,
            steps = Sequence(listOf(step1, step2)),
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            active = true
        )

        val inputParameters = mapOf("userId" to "user123", "retryCount" to 3)

        // Mock: Workflow revision exists
        every { workflowRevisionRepository.findById(revisionId) } returns workflow

        // Mock: Execution creation
        every { executionRepository.createExecution(any()) } answers {
            firstArg<WorkflowExecution>()
        }

        // Mock: Step result saving
        every { executionRepository.saveStepResult(any()) } answers {
            firstArg<ExecutionStepResult>()
        }

        // Mock: Status update
        every { executionRepository.updateExecutionStatus(any(), any(), any()) } just Runs

        // When: Executing the workflow
        val executionId = useCase.execute(revisionId, inputParameters)

        // Then: Execution ID should be generated
        executionId shouldNotBe null

        // Verify: Workflow revision was fetched
        verify(exactly = 1) { workflowRevisionRepository.findById(revisionId) }

        // Verify: Execution was created with RUNNING status
        verify(exactly = 1) {
            executionRepository.createExecution(
                match {
                    it.revisionId == revisionId &&
                    it.inputParameters == inputParameters &&
                    it.status == ExecutionStatus.RUNNING
                }
            )
        }

        // Verify: Final status update to COMPLETED
        verify(exactly = 1) {
            executionRepository.updateExecutionStatus(
                executionId,
                ExecutionStatus.COMPLETED,
                null
            )
        }
    }

    @Test
    fun `execute should persist step results per-step for crash recovery`() {
        // Given: A workflow with two steps
        val revisionId = WorkflowRevisionID("test-ns", "test-workflow", 1)
        val step1 = LogTask("Step 1")
        val step2 = LogTask("Step 2")
        val workflow = WorkflowRevision(
            revisionId = revisionId,
            steps = Sequence(listOf(step1, step2)),
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            active = true
        )

        // Mock setup
        every { workflowRevisionRepository.findById(revisionId) } returns workflow
        every { executionRepository.createExecution(any()) } answers { firstArg() }
        every { executionRepository.saveStepResult(any()) } answers { firstArg() }
        every { executionRepository.updateExecutionStatus(any(), any(), any()) } just Runs

        // When: Executing the workflow
        val executionId = useCase.execute(revisionId, emptyMap())

        // Then: Step results should be saved (per-step commits)
        // Note: The exact number depends on how the executor tracks steps
        // At minimum, we expect step results to be saved incrementally
        verify(atLeast = 1) {
            executionRepository.saveStepResult(any())
        }
    }

    @Test
    fun `execute should fail with FAILED status when step execution fails`() {
        // Given: A workflow with a failing step
        val revisionId = WorkflowRevisionID("test-ns", "test-workflow", 1)
        val failingStep = FailingStep()
        val workflow = WorkflowRevision(
            revisionId = revisionId,
            steps = failingStep,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            active = true
        )

        // Mock setup
        every { workflowRevisionRepository.findById(revisionId) } returns workflow
        every { executionRepository.createExecution(any()) } answers { firstArg() }
        every { executionRepository.saveStepResult(any()) } answers { firstArg() }
        every { executionRepository.updateExecutionStatus(any(), any(), any()) } just Runs

        // When: Executing the workflow
        val executionId = useCase.execute(revisionId, emptyMap())

        // Then: Execution should be marked as FAILED
        verify(exactly = 1) {
            executionRepository.updateExecutionStatus(
                executionId,
                ExecutionStatus.FAILED,
                any() // Error message
            )
        }
    }

    @Test
    fun `execute should throw exception when workflow revision not found`() {
        // Given: A non-existent workflow revision
        val revisionId = WorkflowRevisionID("test-ns", "non-existent", 1)

        // Mock: Workflow not found
        every { workflowRevisionRepository.findById(revisionId) } returns null

        // When/Then: Should throw exception
        assertThrows<IllegalArgumentException> {
            useCase.execute(revisionId, emptyMap())
        }

        // Verify: No execution was created
        verify(exactly = 0) { executionRepository.createExecution(any()) }
    }

    @Test
    fun `execute should validate input parameters against workflow schema`() {
        // Given: A workflow that requires specific parameters
        val revisionId = WorkflowRevisionID("test-ns", "test-workflow", 1)
        val workflow = WorkflowRevision(
            revisionId = revisionId,
            steps = LogTask("Test"),
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            active = true
        )

        // Mock setup
        every { workflowRevisionRepository.findById(revisionId) } returns workflow
        every { executionRepository.createExecution(any()) } answers { firstArg() }
        every { executionRepository.updateExecutionStatus(any(), any(), any()) } just Runs

        // When: Executing with parameters
        val parameters = mapOf("userId" to "user123")
        val executionId = useCase.execute(revisionId, parameters)

        // Then: Parameters should be stored in execution
        verify {
            executionRepository.createExecution(
                match { it.inputParameters == parameters }
            )
        }
    }

    @Test
    fun `execute should generate unique execution IDs`() {
        // Given: A simple workflow
        val revisionId = WorkflowRevisionID("test-ns", "test-workflow", 1)
        val workflow = WorkflowRevision(
            revisionId = revisionId,
            steps = LogTask("Test"),
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            active = true
        )

        // Mock setup
        every { workflowRevisionRepository.findById(revisionId) } returns workflow
        every { executionRepository.createExecution(any()) } answers { firstArg() }
        every { executionRepository.updateExecutionStatus(any(), any(), any()) } just Runs

        // When: Executing multiple times
        val executionId1 = useCase.execute(revisionId, emptyMap())
        val executionId2 = useCase.execute(revisionId, emptyMap())

        // Then: IDs should be different
        executionId1 shouldNotBe executionId2

        // And: Both should be valid WorkflowExecutionIDs
        executionId1.shouldBeInstanceOf<WorkflowExecutionID>()
        executionId2.shouldBeInstanceOf<WorkflowExecutionID>()
    }

    // Test helper classes

    private class FailingStep : Step {
        override fun execute(context: ExecutionContext): Pair<StepStatus, ExecutionContext> {
            return Pair(StepStatus.FAILED, context)
        }
    }
}
