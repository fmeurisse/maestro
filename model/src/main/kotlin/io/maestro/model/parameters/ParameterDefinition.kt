package io.maestro.model.parameters

/**
 * Definition of a workflow input parameter.
 *
 * Defines the schema for a single parameter that can be provided when executing a workflow.
 * Parameters are validated against these definitions before execution begins.
 *
 * @property name Parameter name (must be unique within a workflow revision)
 * @property type Expected parameter type (STRING, INTEGER, FLOAT, BOOLEAN)
 * @property required Whether this parameter must be provided (default: true)
 * @property default Default value to use if parameter is not provided (only applies if required = false)
 * @property description Human-readable description of the parameter's purpose
 */
data class ParameterDefinition(
    val name: String,
    val type: ParameterType,
    val required: Boolean = true,
    val default: Any? = null,
    val description: String? = null
)
