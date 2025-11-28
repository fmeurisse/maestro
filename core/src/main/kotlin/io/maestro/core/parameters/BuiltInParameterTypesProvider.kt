package io.maestro.core.parameters

import io.maestro.model.parameters.*

/**
 * Default parameter types provider for built-in types.
 *
 * Provides the four standard parameter types:
 * - STRING: Text values
 * - INTEGER: Whole numbers
 * - FLOAT: Decimal numbers
 * - BOOLEAN: True/false values
 *
 * This provider is automatically discovered via ServiceLoader.
 */
class BuiltInParameterTypesProvider : ParameterTypesProvider {
    override fun provide(): List<ParameterType> {
        return listOf(
            StringParameterType,
            IntegerParameterType,
            FloatParameterType,
            BooleanParameterType
        )
    }
}