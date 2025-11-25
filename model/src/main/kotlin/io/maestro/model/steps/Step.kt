package io.maestro.model.steps

import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 * Base interface for all workflow steps.
 * 
 * Steps can be either orchestration steps (Sequence, If) that contain other steps,
 * or task steps (LogTask, WorkTask) that perform actual work.
 * 
 * Jackson polymorphism is configured via @JsonTypeInfo to enable serialization/deserialization
 * of different step types. Subtypes are registered at runtime via StepTypeRegistry
 * to support plugin extensibility.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
interface Step
