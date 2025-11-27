package io.maestro.core.executions.usecases

import io.github.oshai.kotlinlogging.KotlinLogging
import io.maestro.core.workflows.IWorkflowRevisionRepository
import io.maestro.core.executions.IWorkflowExecutionRepository
import io.maestro.model.WorkflowRevisionID
import io.maestro.model.execution.*
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.time.Instant

/**
 * Use case for executing a workflow with input parameters.
 *
 * This use case:
 * 1. Validates that the workflow revision exists
 * 2. Creates an execution record with RUNNING status
 * 3. Executes all workflow steps sequentially
 * 4. Persists step results after each step (per-step commits for crash recovery)
 * 5. Updates execution status to COMPLETED or FAILED
 * 6. Returns the execution ID
 *
 * Per SC-002, all state is persisted before returning to ensure 100% durability.
 */
@ApplicationScoped
class ExecuteWorkflowUseCase @Inject constructor(
    private val workflowRevisionRepository: IWorkflowRevisionRepository,
    private val executionRepository: IWorkflowExecutionRepository
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Execute a workflow revision with the provided input parameters.
     *
     * @param revisionId The workflow revision to execute
     * @param inputParameters The input parameters for the workflow
     * @return The execution ID for querying status
     * @throws IllegalArgumentException if the workflow revision does not exist
     */
    fun execute(
        revisionId: WorkflowRevisionID,
        inputParameters: Map<String, Any>
    ): WorkflowExecutionID {
        // Step 1: Fetch the workflow revision
        val workflow = workflowRevisionRepository.findById(revisionId)
            ?: throw IllegalArgumentException(
                "Workflow revision not found: ${revisionId.namespace}/${revisionId.id}/v${revisionId.version}"
            )

        // Step 2: Generate execution ID (UUID v7 for time-ordered sortability)
        val executionId = WorkflowExecutionID.generate()

        // Step 3: Create execution record with RUNNING status
        logger.info { "Starting execution for workflow ${revisionId.namespace}/${revisionId.id}/v${revisionId.version} (executionId=$executionId)" }
        val execution = WorkflowExecution(
            executionId = executionId,
            revisionId = revisionId,
            inputParameters = inputParameters,
            status = ExecutionStatus.RUNNING,
            errorMessage = null,
            startedAt = Instant.now(),
            completedAt = null,
            lastUpdatedAt = Instant.now()
        )
        executionRepository.createExecution(execution)

        // Step 4: Execute workflow steps with automatic persistence
        try {
            // Create step executor for this execution
            val stepExecutor = io.maestro.core.executions.StepExecutor(
                executionId = executionId,
                executionRepository = executionRepository
            )

            // Create execution context with executor for nested orchestration steps
            val context = ExecutionContext(
                inputParameters = inputParameters,
                stepOutputs = emptyMap(),
                stepExecutor = stepExecutor
            )

            // Execute the workflow steps tree
            // The executor handles persistence for all steps (including nested ones)
            val (finalStatus, _) = stepExecutor.executeSequence(
                steps = workflow.steps,
                context = context
            )

            // Step 5: Mark execution as COMPLETED or FAILED based on result
            if (finalStatus == StepStatus.COMPLETED) {
                logger.info { "Execution completed successfully: $executionId" }
                executionRepository.updateExecutionStatus(
                    executionId = executionId,
                    status = ExecutionStatus.COMPLETED,
                    errorMessage = null
                )
            } else {
                logger.warn { "Execution failed with step status: $finalStatus (executionId=$executionId)" }
                executionRepository.updateExecutionStatus(
                    executionId = executionId,
                    status = ExecutionStatus.FAILED,
                    errorMessage = "Workflow execution failed"
                )
            }

        } catch (e: Exception) {
            // Step 6: Mark execution as FAILED with error message
            logger.error(e) { "Execution failed with exception: $executionId - ${e.message}" }
            executionRepository.updateExecutionStatus(
                executionId = executionId,
                status = ExecutionStatus.FAILED,
                errorMessage = e.message ?: "Unknown error: ${e::class.simpleName}"
            )
        }

        return executionId
    }
}
