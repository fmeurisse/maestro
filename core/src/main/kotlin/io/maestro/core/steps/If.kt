package io.maestro.core.steps

import io.maestro.model.execution.ExecutionContext
import io.maestro.model.execution.StepStatus
import io.maestro.model.steps.OrchestrationStep
import io.maestro.model.steps.Step

data class If(
    val condition: String, // You might want to use a more structured expression class here
    val ifTrue: Step,      // This can be a TaskList or a single Action
    val ifFalse: Step? = null // This can also be a TaskList or a single Action
) : OrchestrationStep {

    override fun execute(context: ExecutionContext): Pair<StepStatus, ExecutionContext> {
        val conditionResult = evaluateCondition(condition, context)

        return if (conditionResult) {
            // Execute ifTrue branch with automatic persistence
            context.stepExecutor.executeAndPersist(ifTrue, context)
        } else {
            // Execute ifFalse branch if provided, otherwise no-op
            ifFalse?.let { context.stepExecutor.executeAndPersist(it, context) }
                ?: Pair(StepStatus.COMPLETED, context)
        }
    }
    
    /**
     * Evaluate condition expression against execution context.
     * Simple implementation: supports basic equality checks like "${param} == 'value'"
     */
    private fun evaluateCondition(condition: String, context: ExecutionContext): Boolean {
        // Simple condition evaluation - supports "${param} == 'value'" pattern
        val equalityPattern = Regex("""\$\{(\w+)\}\s*==\s*['"]([^'"]+)['"]""")
        val match = equalityPattern.find(condition)
        
        if (match != null) {
            val paramName = match.groupValues[1]
            val expectedValue = match.groupValues[2]
            val actualValue = context.getParameter(paramName)?.toString()
            return actualValue == expectedValue
        }
        
        // Fallback: treat condition as parameter name, check if truthy
        val paramValue = context.getParameter(condition)
        return when (paramValue) {
            is Boolean -> paramValue
            is String -> paramValue.lowercase() in listOf("true", "1", "yes", "on")
            is Number -> paramValue.toDouble() != 0.0
            else -> paramValue != null
        }
    }
    
    companion object {
        const val TYPE_NAME = "If"
    }
}
