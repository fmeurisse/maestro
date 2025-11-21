-- Flyway/Liquibase compatible migration script
-- Version: V001
-- Description: Create workflow_revisions table with dual storage (TEXT + JSONB + computed columns)

-- Workflow Revisions Table
CREATE TABLE workflow_revisions (
    -- Dual storage: TEXT preserves YAML formatting, JSONB stores complete WorkflowRevision
    yaml_source TEXT NOT NULL,
    revision_data JSONB NOT NULL,

    -- Computed columns from JSONB for efficient querying and indexing
    namespace VARCHAR(100) GENERATED ALWAYS AS (revision_data->>'namespace') STORED,
    id VARCHAR(100) GENERATED ALWAYS AS (revision_data->>'id') STORED,
    version BIGINT GENERATED ALWAYS AS ((revision_data->>'version')::BIGINT) STORED,
    name VARCHAR(255) GENERATED ALWAYS AS (revision_data->>'name') STORED,
    active BOOLEAN GENERATED ALWAYS AS ((revision_data->>'active')::BOOLEAN) STORED,
    created_at TIMESTAMP WITH TIME ZONE GENERATED ALWAYS AS ((revision_data->>'createdAt')::TIMESTAMP WITH TIME ZONE) STORED,
    updated_at TIMESTAMP WITH TIME ZONE GENERATED ALWAYS AS ((revision_data->>'updatedAt')::TIMESTAMP WITH TIME ZONE) STORED,

    -- Constraints on computed columns
    PRIMARY KEY (namespace, id, version),
    CONSTRAINT valid_namespace CHECK (namespace ~ '^[a-zA-Z0-9_-]+$'),
    CONSTRAINT valid_id CHECK (id ~ '^[a-zA-Z0-9_-]+$'),
    CONSTRAINT positive_version CHECK (version > 0)
);

-- Indexes for query performance
CREATE INDEX idx_workflow_active
ON workflow_revisions(namespace, id, active)
WHERE active = TRUE;

CREATE INDEX idx_workflow_namespace
ON workflow_revisions(namespace);

CREATE INDEX idx_workflow_created_at
ON workflow_revisions(created_at DESC);

CREATE INDEX idx_workflow_revision_data_gin
ON workflow_revisions USING GIN(revision_data jsonb_path_ops);

-- Table and column comments
COMMENT ON TABLE workflow_revisions IS 'Stores workflow revisions with dual storage: YAML source in TEXT, parsed data in JSONB';
COMMENT ON COLUMN workflow_revisions.yaml_source IS 'Original YAML definition preserving formatting and comments';
COMMENT ON COLUMN workflow_revisions.revision_data IS 'Complete WorkflowRevision as JSONB (without yamlSource field)';
