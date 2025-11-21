-- Workflow Revisions Table
-- Dual storage: yaml_source TEXT + revision_data JSONB + computed columns
-- This schema supports the two-entity pattern:
--   - WorkflowRevision (without YAML source)
--   - WorkflowRevisionWithSource (with YAML source)

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
    created_at TIMESTAMP WITH TIME ZONE GENERATED ALWAYS AS ((revision_data->>'createdAt')::TIMESTAMP WITH TIME ZONE) STORED,
    updated_at TIMESTAMP WITH TIME ZONE GENERATED ALWAYS AS ((revision_data->>'updatedAt')::TIMESTAMP WITH TIME ZONE) STORED,

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

-- Index for timestamp-based queries (audit, history)
CREATE INDEX IF NOT EXISTS idx_workflow_created_at
ON workflow_revisions(created_at DESC);

-- GIN index on JSONB for querying step types and nested properties
CREATE INDEX IF NOT EXISTS idx_workflow_revision_data_gin
ON workflow_revisions USING GIN(revision_data jsonb_path_ops);

-- Comments for documentation
COMMENT ON TABLE workflow_revisions IS 'Stores workflow revisions with dual storage: YAML source in TEXT, parsed data in JSONB';
COMMENT ON COLUMN workflow_revisions.yaml_source IS 'Original YAML definition preserving formatting and comments';
COMMENT ON COLUMN workflow_revisions.revision_data IS 'Complete WorkflowRevision as JSONB (without yamlSource field)';
COMMENT ON COLUMN workflow_revisions.namespace IS 'Computed: Logical isolation boundary (e.g., production, staging)';
COMMENT ON COLUMN workflow_revisions.id IS 'Computed: Workflow identifier within namespace';
COMMENT ON COLUMN workflow_revisions.version IS 'Computed: Sequential version number (1, 2, 3...)';
COMMENT ON COLUMN workflow_revisions.name IS 'Computed: Human-readable workflow name';
COMMENT ON COLUMN workflow_revisions.active IS 'Computed: Whether this revision is active for execution';
COMMENT ON COLUMN workflow_revisions.created_at IS 'Computed: UTC timestamp when revision was created (immutable)';
COMMENT ON COLUMN workflow_revisions.updated_at IS 'Computed: UTC timestamp of last modification';
