-- Workflow Revisions Table (Simplified for testing)
-- Dual storage: yaml_source TEXT + revision_data JSONB + computed columns
-- Note: Timestamp computed columns removed due to PostgreSQL immutability constraints in tests

CREATE TABLE IF NOT EXISTS workflow_revisions (
    -- Dual storage: TEXT preserves YAML formatting, JSONB stores complete WorkflowRevision
    yaml_source TEXT NOT NULL,              -- Original YAML with comments/formatting
    revision_data JSONB NOT NULL,           -- Complete WorkflowRevision (without yamlSource to avoid duplication)

    -- Computed columns from JSONB for efficient querying and indexing
    namespace VARCHAR(100) GENERATED ALWAYS AS (revision_data->>'namespace') STORED,
    id VARCHAR(100) GENERATED ALWAYS AS (revision_data->>'id') STORED,
    version BIGINT GENERATED ALWAYS AS ((revision_data->>'version')::BIGINT) STORED,
    name VARCHAR(255) GENERATED ALWAYS AS (revision_data->>'name') STORED,
    active BOOLEAN GENERATED ALWAYS AS ((revision_data->>'active')::BOOLEAN) STORED,

    -- Constraints on computed columns
    PRIMARY KEY (namespace, id, version),
    CONSTRAINT valid_namespace CHECK (namespace ~ '^[a-zA-Z0-9_-]+$'),
    CONSTRAINT valid_id CHECK (id ~ '^[a-zA-Z0-9_-]+$'),
    CONSTRAINT positive_version CHECK (version > 0)
);

-- Index for finding active revisions (common query: "get active revisions for workflow X")
CREATE INDEX IF NOT EXISTS idx_workflow_active
ON workflow_revisions(namespace, id, active)
WHERE active = TRUE;

-- Index for listing workflows in a namespace
CREATE INDEX IF NOT EXISTS idx_workflow_namespace
ON workflow_revisions(namespace);

-- GIN index on JSONB for querying step types and nested properties
CREATE INDEX IF NOT EXISTS idx_workflow_revision_data_gin
ON workflow_revisions USING GIN(revision_data jsonb_path_ops);