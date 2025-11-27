package io.maestro.core.workflows.steps

import io.maestro.model.steps.Step
import kotlin.reflect.KClass

/**
 * Core step types provider that registers the built-in step types
 * available in the core module.
 * 
 * This provider registers:
 * - Sequence: Sequential execution of multiple steps
 * - If: Conditional execution based on a condition
 * - LogTask: Task that logs a message
 * 
 * Registered via META-INF/services/io.maestro.core.steps.StepTypesProvider
 */
class CoreStepTypesProvider : StepTypesProvider {
    
    override fun provideStepTypes(): Map<String, KClass<out Step>> {
        return mapOf(
            Sequence.TYPE_NAME to Sequence::class,
            If.TYPE_NAME to If::class,
            LogTask.TYPE_NAME to LogTask::class
        )
    }
}
