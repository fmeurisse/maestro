package io.maestro.model.execution

/**
 * Carries execution state through the workflow step tree.
 * 
 * ExecutionContext is immutable and passed through the execution visitor for context propagation.
 * It contains:
 * - Original workflow input parameters
 * - Accumulated outputs from completed steps (keyed by stepId)
 * 
 * Scoped hierarchically for nested orchestration steps.
 */
data class ExecutionContext(
    /**
     * Original workflow input parameters
     */
    val inputParameters: Map<String, Any>,
    
    /**
     * Accumulated outputs from completed steps, keyed by stepId.
     * Steps can access outputs from previous steps via getStepOutput().
     */
    val stepOutputs: Map<String, Any> = emptyMap()
) {
    init {
        require(inputParameters != null) { "ExecutionContext.inputParameters must not be null" }
    }
    
    /**
     * Returns a new ExecutionContext with the specified step output added.
     * 
     * @param stepId The identifier of the step that produced this output
     * @param output The output value from the step
     * @return New ExecutionContext instance with the output added
     */
    fun withStepOutput(stepId: String, output: Any): ExecutionContext {
        return copy(stepOutputs = stepOutputs + (stepId to output))
    }
    
    /**
     * Retrieves an input parameter value by name.
     * 
     * @param name Parameter name
     * @return Parameter value, or null if not found
     */
    fun getParameter(name: String): Any? = inputParameters[name]
    
    /**
     * Retrieves output from a previous step by stepId.
     * 
     * @param stepId Step identifier
     * @return Step output value, or null if step hasn't executed or has no output
     */
    fun getStepOutput(stepId: String): Any? = stepOutputs[stepId]
}
