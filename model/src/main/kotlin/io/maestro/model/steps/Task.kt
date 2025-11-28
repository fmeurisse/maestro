package io.maestro.model.steps

/**
 * Base interface for all task steps.
 * 
 * Tasks are concrete work units that perform actual operations.
 * All tasks must implement the execute(context: ExecutionContext) method
 * from the Step interface.
 */
interface Task : Step {
    // Legacy execute() method removed - use execute(context: ExecutionContext) instead
}
