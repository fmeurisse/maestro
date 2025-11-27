package io.maestro.core.workflows.steps

import io.maestro.core.executions.NoOpStepExecutor
import io.maestro.model.execution.ExecutionContext
import io.maestro.model.execution.StepStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class LogTaskUnitTest {

    @Test
    fun `execute should return COMPLETED status`() {
        // Given: A LogTask with a simple message
        val logTask = LogTask(message = "Test log message")

        val context = ExecutionContext(inputParameters = emptyMap(), stepExecutor = NoOpStepExecutor.INSTANCE)

        // When: Executing the log task
        val (status, resultContext) = logTask.execute(context)

        // Then: Status should be COMPLETED
        assertEquals(StepStatus.COMPLETED, status)
    }

    @Test
    fun `execute should not modify context`() {
        // Given: A LogTask
        val logTask = LogTask(message = "Test log message")

        val context = ExecutionContext(
            inputParameters = mapOf("param1" to "value1"),
            stepOutputs = mapOf("previousStep" to "previousOutput"),
            stepExecutor = NoOpStepExecutor.INSTANCE
        )

        // When: Executing the log task
        val (_, resultContext) = logTask.execute(context)

        // Then: Context should remain unchanged
        assertEquals(context.inputParameters, resultContext.inputParameters)
        assertEquals(context.stepOutputs, resultContext.stepOutputs)
    }

    @Test
    fun `execute should log message to standard output`() {
        // Given: A LogTask with a specific message
        val message = "Unique test message 12345"
        val logTask = LogTask(message = message)

        val context = ExecutionContext(inputParameters = emptyMap(), stepExecutor = NoOpStepExecutor.INSTANCE)

        // Capture console output
        val outputStream = ByteArrayOutputStream()
        val printStream = PrintStream(outputStream)
        val originalOut = System.out
        System.setOut(printStream)

        try {
            // When: Executing the log task
            val (status, _) = logTask.execute(context)

            // Then: Should complete successfully
            assertEquals(StepStatus.COMPLETED, status)

            // And: Message should be logged to stdout
            val output = outputStream.toString()
            assertTrue(output.contains(message),
                "Expected log output to contain '$message', got: $output")
        } finally {
            System.setOut(originalOut)
        }
    }

    @Test
    fun `execute should handle empty message`() {
        // Given: A LogTask with an empty message
        val logTask = LogTask(message = "")

        val context = ExecutionContext(inputParameters = emptyMap(), stepExecutor = NoOpStepExecutor.INSTANCE)

        // When: Executing the log task
        val (status, resultContext) = logTask.execute(context)

        // Then: Should complete successfully
        assertEquals(StepStatus.COMPLETED, status)
        assertEquals(context, resultContext)
    }

    @Test
    fun `execute should handle message with special characters`() {
        // Given: A LogTask with special characters in the message
        val logTask = LogTask(message = "Log: \${param} with \"quotes\" and 'apostrophes' and newlines\n\t")

        val context = ExecutionContext(inputParameters = emptyMap(), stepExecutor = NoOpStepExecutor.INSTANCE)

        // When: Executing the log task
        val (status, resultContext) = logTask.execute(context)

        // Then: Should complete successfully without errors
        assertEquals(StepStatus.COMPLETED, status)
        assertEquals(context, resultContext)
    }

    @Test
    fun `execute should always succeed even with null-like message content`() {
        // Given: A LogTask with message containing null-related content
        val logTask = LogTask(message = "null value: null")

        val context = ExecutionContext(inputParameters = emptyMap(), stepExecutor = NoOpStepExecutor.INSTANCE)

        // When: Executing the log task
        val (status, resultContext) = logTask.execute(context)

        // Then: Should complete successfully (logging never fails)
        assertEquals(StepStatus.COMPLETED, status)
        assertEquals(context, resultContext)
    }
}
