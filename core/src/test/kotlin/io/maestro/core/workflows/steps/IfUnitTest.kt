package io.maestro.core.workflows.steps

import io.maestro.core.executions.NoOpStepExecutor
import io.maestro.model.execution.ExecutionContext
import io.maestro.model.execution.StepStatus
import io.maestro.model.steps.Step
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class IfUnitTest {

    @Test
    fun `execute should run ifTrue branch when parameter condition is true`() {
        // Given: An If step with a parameter-based condition that is true
        val thenStep = TestSuccessStep("thenStep", outputValue = "then-output")
        val elseStep = TestSuccessStep("elseStep", outputValue = "else-output")
        val ifStep = If(
            condition = "enabled", // Checks context.getParameter("enabled")
            ifTrue = thenStep,
            ifFalse = elseStep
        )

        val context = ExecutionContext(inputParameters = mapOf("enabled" to true), stepExecutor = NoOpStepExecutor.INSTANCE)

        // When: Executing the If step
        val (status, resultContext) = ifStep.execute(context)

        // Then: Status should be COMPLETED
        assertEquals(StepStatus.COMPLETED, status)

        // And: Then branch should have executed
        assertEquals("then-output", resultContext.getStepOutput("thenStep"))

        // And: Else branch should NOT have executed
        assertNull(resultContext.getStepOutput("elseStep"))
    }

    @Test
    fun `execute should run ifFalse branch when parameter condition is false`() {
        // Given: An If step with a parameter-based condition that is false
        val thenStep = TestSuccessStep("thenStep", outputValue = "then-output")
        val elseStep = TestSuccessStep("elseStep", outputValue = "else-output")
        val ifStep = If(
            condition = "enabled", // Checks context.getParameter("enabled")
            ifTrue = thenStep,
            ifFalse = elseStep
        )

        val context = ExecutionContext(inputParameters = mapOf("enabled" to false), stepExecutor = NoOpStepExecutor.INSTANCE)

        // When: Executing the If step
        val (status, resultContext) = ifStep.execute(context)

        // Then: Status should be COMPLETED
        assertEquals(StepStatus.COMPLETED, status)

        // And: Else branch should have executed
        assertEquals("else-output", resultContext.getStepOutput("elseStep"))

        // And: Then branch should NOT have executed
        assertNull(resultContext.getStepOutput("thenStep"))
    }

    @Test
    fun `execute should evaluate equality condition against context parameters`() {
        // Given: An If step with an equality condition
        val thenStep = TestSuccessStep("thenStep", outputValue = "enabled")
        val elseStep = TestSuccessStep("elseStep", outputValue = "disabled")
        val ifStep = If(
            condition = "\${environment} == 'production'", // Equality check
            ifTrue = thenStep,
            ifFalse = elseStep
        )

        val context = ExecutionContext(inputParameters = mapOf("environment" to "production"), stepExecutor = NoOpStepExecutor.INSTANCE)

        // When: Executing the If step
        val (status, resultContext) = ifStep.execute(context)

        // Then: Then branch should execute because environment equals production
        assertEquals(StepStatus.COMPLETED, status)
        assertEquals("enabled", resultContext.getStepOutput("thenStep"))
    }

    @Test
    fun `execute should treat string values as truthy or falsy`() {
        // Given: An If step checking a string parameter
        val thenStep = TestSuccessStep("thenStep", outputValue = "yes")
        val elseStep = TestSuccessStep("elseStep", outputValue = "no")

        // Test various truthy string values
        val truthyValues = listOf("true", "TRUE", "1", "yes", "on")
        for (value in truthyValues) {
            val ifStep = If(
                condition = "flag",
                ifTrue = thenStep,
                ifFalse = elseStep
            )
            val context = ExecutionContext(inputParameters = mapOf("flag" to value), stepExecutor = NoOpStepExecutor.INSTANCE)
            val (status, resultContext) = ifStep.execute(context)

            assertEquals(StepStatus.COMPLETED, status, "Failed for truthy value: $value")
            assertEquals("yes", resultContext.getStepOutput("thenStep"), "Failed for truthy value: $value")
        }
    }

    @Test
    fun `execute should return FAILED when ifTrue branch fails`() {
        // Given: An If step where the ifTrue branch fails
        val thenStep = TestFailingStep()
        val elseStep = TestSuccessStep("elseStep", outputValue = "else-output")
        val ifStep = If(
            condition = "enabled",
            ifTrue = thenStep,
            ifFalse = elseStep
        )

        val context = ExecutionContext(inputParameters = mapOf("enabled" to true), stepExecutor = NoOpStepExecutor.INSTANCE)

        // When: Executing the If step
        val (status, _) = ifStep.execute(context)

        // Then: Status should be FAILED
        assertEquals(StepStatus.FAILED, status)
    }

    @Test
    fun `execute should return FAILED when ifFalse branch fails`() {
        // Given: An If step where the ifFalse branch fails
        val thenStep = TestSuccessStep("thenStep", outputValue = "then-output")
        val elseStep = TestFailingStep()
        val ifStep = If(
            condition = "enabled",
            ifTrue = thenStep,
            ifFalse = elseStep
        )

        val context = ExecutionContext(inputParameters = mapOf("enabled" to false), stepExecutor = NoOpStepExecutor.INSTANCE)

        // When: Executing the If step
        val (status, _) = ifStep.execute(context)

        // Then: Status should be FAILED
        assertEquals(StepStatus.FAILED, status)
    }

    @Test
    fun `execute with null ifFalse branch should complete when condition is false`() {
        // Given: An If step without else branch
        val thenStep = TestSuccessStep("thenStep", outputValue = "then-output")
        val ifStep = If(
            condition = "enabled",
            ifTrue = thenStep,
            ifFalse = null
        )

        val context = ExecutionContext(inputParameters = mapOf("enabled" to false), stepExecutor = NoOpStepExecutor.INSTANCE)

        // When: Executing the If step
        val (status, resultContext) = ifStep.execute(context)

        // Then: Status should be COMPLETED (no else branch to execute)
        assertEquals(StepStatus.COMPLETED, status)

        // And: Context should be unchanged (then branch didn't execute)
        assertNull(resultContext.getStepOutput("thenStep"))
    }

    @Test
    fun `execute should treat numeric zero as falsy`() {
        // Given: An If step checking numeric parameters
        val thenStep = TestSuccessStep("thenStep", outputValue = "yes")
        val elseStep = TestSuccessStep("elseStep", outputValue = "no")
        val ifStep = If(
            condition = "count",
            ifTrue = thenStep,
            ifFalse = elseStep
        )

        // Test zero
        val contextZero = ExecutionContext(inputParameters = mapOf("count" to 0), stepExecutor = NoOpStepExecutor.INSTANCE)
        val (statusZero, resultContextZero) = ifStep.execute(contextZero)

        assertEquals(StepStatus.COMPLETED, statusZero)
        assertEquals("no", resultContextZero.getStepOutput("elseStep"))

        // Test non-zero
        val contextNonZero = ExecutionContext(inputParameters = mapOf("count" to 5), stepExecutor = NoOpStepExecutor.INSTANCE)
        val (statusNonZero, resultContextNonZero) = ifStep.execute(contextNonZero)

        assertEquals(StepStatus.COMPLETED, statusNonZero)
        assertEquals("yes", resultContextNonZero.getStepOutput("thenStep"))
    }

    // Test helper classes

    private class TestSuccessStep(
        private val stepId: String,
        private val outputValue: Any
    ) : Step {
        override fun execute(context: ExecutionContext): Pair<StepStatus, ExecutionContext> {
            return Pair(
                StepStatus.COMPLETED,
                context.withStepOutput(stepId, outputValue)
            )
        }
    }

    private class TestFailingStep : Step {
        override fun execute(context: ExecutionContext): Pair<StepStatus, ExecutionContext> {
            return Pair(StepStatus.FAILED, context)
        }
    }
}
