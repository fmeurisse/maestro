package io.maestro.core.workflows.steps

import io.maestro.model.steps.Step
import kotlin.reflect.KClass

/**
 * Test step types provider for unit testing StepTypeRegistry discovery.
 * 
 * This provider registers test step types that are only available during testing.
 * Registered via META-INF/services/io.maestro.core.steps.StepTypesProvider in test resources.
 */
class TestStepTypesProvider : StepTypesProvider {
    
    override fun provideStepTypes(): Map<String, KClass<out Step>> {
        return mapOf(
            TestTask.TYPE_NAME to TestTask::class
        )
    }
}
