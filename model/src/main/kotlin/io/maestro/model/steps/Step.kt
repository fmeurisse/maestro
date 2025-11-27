package io.maestro.model.steps

import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.maestro.model.execution.ExecutionContext
import io.maestro.model.execution.StepStatus

/**
 * Base interface for all workflow steps.
 * 
 * Steps can be either orchestration steps (Sequence, If) that contain other steps,
 * or task steps (LogTask, WorkTask) that perform actual work.
 * 
 * Jackson polymorphism is configured via @JsonTypeInfo to enable serialization/deserialization
 * of different step types. Subtypes are registered at runtime via StepTypeRegistry
 * to support plugin extensibility.
 * 
 * Each step implements execute() to perform its work and return its status along with
 * an updated execution context containing any outputs.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
fun interface Step {
    /**
     * Execute this step with the given execution context.
     * 
     * @param context The execution context containing input parameters and step outputs
     * @return Pair of (StepStatus, ExecutionContext) - the step's execution status and updated context
     */
    fun execute(context: ExecutionContext): Pair<StepStatus, ExecutionContext>
}
