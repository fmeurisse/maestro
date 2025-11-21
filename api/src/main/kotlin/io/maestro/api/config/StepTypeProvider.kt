package io.maestro.api.config

import io.maestro.model.steps.Step
import kotlin.reflect.KClass

/**
 * Interface for providing step types that can be registered at runtime.
 * 
 * Plugins can implement this interface to register custom step types
 * without modifying core code. The ServiceLoader pattern is used to
 * discover implementations at application startup.
 * 
 * Example:
 * ```kotlin
 * class CustomStepTypeProvider : StepTypeProvider {
 *     override fun provideStepTypes(): Map<String, KClass<out Step>> = mapOf(
 *         "CustomHttpTask" to CustomHttpTask::class,
 *         "CustomEmailTask" to CustomEmailTask::class
 *     )
 * }
 * ```
 * 
 * Register in META-INF/services/io.maestro.api.config.StepTypeProvider
 */
interface StepTypeProvider {
    /**
     * Returns a map of step type names to their Kotlin classes.
     * The type name is used in JSON/YAML as the "type" field.
     * 
     * @return Map of type name to KClass
     */
    fun provideStepTypes(): Map<String, KClass<out Step>>
}
