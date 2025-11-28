--liquibase formatted sql

--changeset maestro:V002-001 splitStatements:false
--comment: Create workflow_executions table for tracking workflow execution state
CREATE TABLE workflow_executions (
    execution_id VARCHAR(100) PRIMARY KEY,
    revision_namespace VARCHAR(255) NOT NULL,
    revision_id VARCHAR(255) NOT NULL,
    revision_version INT NOT NULL,
    input_parameters JSONB NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    error_message TEXT,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    last_updated_at TIMESTAMPTZ NOT NULL,
    FOREIGN KEY (revision_namespace, revision_id, revision_version)
        REFERENCES workflow_revisions(namespace, id, version)
);

--comment: Create execution_step_results table for tracking individual step execution outcomes
CREATE TABLE execution_step_results (
    result_id VARCHAR(100) PRIMARY KEY,
    execution_id VARCHAR(100) NOT NULL REFERENCES workflow_executions(execution_id) ON DELETE CASCADE,
    step_index INT NOT NULL,
    step_id VARCHAR(255) NOT NULL,
    step_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'SKIPPED')),
    input_data JSONB,
    output_data JSONB,
    error_message TEXT,
    error_details JSONB,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL,
    UNIQUE (execution_id, step_index)
);

--comment: Create index on execution status and last_updated_at for efficient status queries
CREATE INDEX idx_executions_status ON workflow_executions(status, last_updated_at);

--comment: Create index on revision reference for efficient workflow history queries
CREATE INDEX idx_executions_revision ON workflow_executions(revision_namespace, revision_id, revision_version);

--comment: Create index on started_at for chronological sorting
CREATE INDEX idx_executions_started ON workflow_executions(started_at DESC);

--comment: Create index on execution_id and step_index for efficient step result queries
CREATE INDEX idx_step_results_execution ON execution_step_results(execution_id, step_index);

--comment: Create index on execution_id and status for filtering step results by status
CREATE INDEX idx_step_results_status ON execution_step_results(execution_id, status);

--comment: Add table and column comments
COMMENT ON TABLE workflow_executions IS 'Stores workflow execution state with input parameters and overall status';
COMMENT ON COLUMN workflow_executions.execution_id IS 'Unique execution identifier (UUID v7, time-ordered)';
COMMENT ON COLUMN workflow_executions.revision_namespace IS 'Foreign key to workflow_revisions.namespace';
COMMENT ON COLUMN workflow_executions.revision_id IS 'Foreign key to workflow_revisions.id';
COMMENT ON COLUMN workflow_executions.revision_version IS 'Foreign key to workflow_revisions.version';
COMMENT ON COLUMN workflow_executions.input_parameters IS 'Input parameters provided for execution (JSONB)';
COMMENT ON COLUMN workflow_executions.status IS 'Execution status: PENDING, RUNNING, COMPLETED, FAILED, CANCELLED';
COMMENT ON COLUMN workflow_executions.error_message IS 'Human-readable error message if status = FAILED';
COMMENT ON COLUMN workflow_executions.started_at IS 'Execution start timestamp (UTC)';
COMMENT ON COLUMN workflow_executions.completed_at IS 'Execution completion timestamp (UTC, null while running)';
COMMENT ON COLUMN workflow_executions.last_updated_at IS 'Last state change timestamp (UTC)';

COMMENT ON TABLE execution_step_results IS 'Stores execution results for each workflow step';
COMMENT ON COLUMN execution_step_results.result_id IS 'Unique result identifier (UUID v4)';
COMMENT ON COLUMN execution_step_results.execution_id IS 'Foreign key to workflow_executions.execution_id';
COMMENT ON COLUMN execution_step_results.step_index IS 'Ordinal position in execution sequence (0-based)';
COMMENT ON COLUMN execution_step_results.step_id IS 'Step identifier from workflow definition';
COMMENT ON COLUMN execution_step_results.step_type IS 'Step type name (e.g., Sequence, If, WorkTask, LogTask)';
COMMENT ON COLUMN execution_step_results.status IS 'Step status: PENDING, RUNNING, COMPLETED, FAILED, SKIPPED';
COMMENT ON COLUMN execution_step_results.input_data IS 'Step input context at execution time (JSONB)';
COMMENT ON COLUMN execution_step_results.output_data IS 'Step output/return value (JSONB, null for failed/skipped steps)';
COMMENT ON COLUMN execution_step_results.error_message IS 'Human-readable error message if status = FAILED';
COMMENT ON COLUMN execution_step_results.error_details IS 'Detailed error information (ErrorInfo as JSONB)';
COMMENT ON COLUMN execution_step_results.started_at IS 'Step execution start time (UTC)';
COMMENT ON COLUMN execution_step_results.completed_at IS 'Step execution completion time (UTC)';

--rollback DROP INDEX IF EXISTS idx_step_results_status;
--rollback DROP INDEX IF EXISTS idx_step_results_execution;
--rollback DROP INDEX IF EXISTS idx_executions_started;
--rollback DROP INDEX IF EXISTS idx_executions_revision;
--rollback DROP INDEX IF EXISTS idx_executions_status;
--rollback DROP TABLE IF EXISTS execution_step_results;
--rollback DROP TABLE IF EXISTS workflow_executions;
