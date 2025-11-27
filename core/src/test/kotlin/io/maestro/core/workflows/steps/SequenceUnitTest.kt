package io.maestro.core.workflows.steps

import io.maestro.core.executions.NoOpStepExecutor
import io.maestro.model.execution.ExecutionContext
import io.maestro.model.execution.StepStatus
import io.maestro.model.steps.Step
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class SequenceUnitTest {

    @Test
    fun `execute with all steps succeeding should return COMPLETED`() {
        // Given: A sequence with two successful steps
        val step1 = TestSuccessStep("step1", outputValue = "output1")
        val step2 = TestSuccessStep("step2", outputValue = "output2")
        val sequence = Sequence(steps = listOf(step1, step2))

        val context = ExecutionContext(
            inputParameters = mapOf("input" to "value"),
            stepExecutor = NoOpStepExecutor.INSTANCE
        )

        // When: Executing the sequence
        val (status, resultContext) = sequence.execute(context)

        // Then: Status should be COMPLETED
        assertEquals(StepStatus.COMPLETED, status)

        // And: Context should contain outputs from both steps
        assertEquals("output1", resultContext.getStepOutput("step1"))
        assertEquals("output2", resultContext.getStepOutput("step2"))
    }

    @Test
    fun `execute with failing step should fail fast and return FAILED`() {
        // Given: A sequence with a successful step followed by a failing step and another step
        val step1 = TestSuccessStep("step1", outputValue = "output1")
        val step2 = TestFailingStep()
        val step3 = TestSuccessStep("step3", outputValue = "output3")
        val sequence = Sequence(steps = listOf(step1, step2, step3))

        val context = ExecutionContext(
            inputParameters = mapOf("input" to "value"),
            stepExecutor = NoOpStepExecutor.INSTANCE
        )

        // When: Executing the sequence
        val (status, resultContext) = sequence.execute(context)

        // Then: Status should be FAILED
        assertEquals(StepStatus.FAILED, status)

        // And: Context should contain output from step1 only (step2 failed, step3 never executed)
        assertEquals("output1", resultContext.getStepOutput("step1"))
        assertNull(resultContext.getStepOutput("step3"))
    }

    @Test
    fun `execute should propagate context through all steps`() {
        // Given: A sequence where steps depend on previous step outputs
        val step1 = TestSuccessStep("step1", outputValue = "value1")
        val step2 = TestContextReadingStep("step2", readFromStep = "step1")
        val sequence = Sequence(steps = listOf(step1, step2))

        val context = ExecutionContext(
            inputParameters = mapOf("initialParam" to "initialValue"),
            stepExecutor = NoOpStepExecutor.INSTANCE
        )

        // When: Executing the sequence
        val (status, resultContext) = sequence.execute(context)

        // Then: Status should be COMPLETED
        assertEquals(StepStatus.COMPLETED, status)

        // And: Step2 should have read step1's output
        assertEquals("read:value1", resultContext.getStepOutput("step2"))
    }

    @Test
    fun `execute with empty step list should return COMPLETED`() {
        // Given: An empty sequence
        val sequence = Sequence(steps = emptyList())
        val context = ExecutionContext(
            inputParameters = emptyMap(),
            stepExecutor = NoOpStepExecutor.INSTANCE
        )

        // When: Executing the sequence
        val (status, resultContext) = sequence.execute(context)

        // Then: Status should be COMPLETED
        assertEquals(StepStatus.COMPLETED, status)

        // And: Context should be unchanged
        assertEquals(context, resultContext)
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

    private class TestContextReadingStep(
        private val stepId: String,
        private val readFromStep: String
    ) : Step {
        override fun execute(context: ExecutionContext): Pair<StepStatus, ExecutionContext> {
            val previousOutput = context.getStepOutput(readFromStep)
            return Pair(
                StepStatus.COMPLETED,
                context.withStepOutput(stepId, "read:$previousOutput")
            )
        }
    }
}
