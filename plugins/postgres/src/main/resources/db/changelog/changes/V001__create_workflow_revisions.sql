--liquibase formatted sql

--changeset maestro:V001-001 splitStatements:false
--comment: Create workflow_revisions table with dual storage (TEXT + JSONB + computed columns)
CREATE TABLE workflow_revisions (
    -- Dual storage: TEXT preserves YAML formatting, JSONB stores complete WorkflowRevision
    yaml_source TEXT NOT NULL,
    revision_data JSONB NOT NULL,
    
    -- Computed columns from JSONB for efficient querying and indexing
    namespace VARCHAR(100) GENERATED ALWAYS AS (jsonb_extract_path_text(revision_data, 'namespace')) STORED,
    id VARCHAR(100) GENERATED ALWAYS AS (jsonb_extract_path_text(revision_data, 'id')) STORED,
    version INT GENERATED ALWAYS AS ((jsonb_extract_path_text(revision_data, 'version'))::int) STORED,
    name VARCHAR(255) GENERATED ALWAYS AS (jsonb_extract_path_text(revision_data, 'name')) STORED,
    active BOOLEAN GENERATED ALWAYS AS ((jsonb_extract_path_text(revision_data, 'active'))::boolean) STORED,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,
    -- Constraints on computed columns
    PRIMARY KEY (namespace, id, version),
    CONSTRAINT valid_namespace CHECK (namespace ~ '^[a-zA-Z0-9_-]+$'),
    CONSTRAINT valid_id CHECK (id ~ '^[a-zA-Z0-9_-]+$'),
    CONSTRAINT positive_version CHECK (version > 0)
);
--comment: Create trigger to fill timestamps from revision_data
CREATE FUNCTION workflow_revisions_fill_timestamps() RETURNS trigger AS $$
BEGIN
    NEW.created_at := (NEW.revision_data->>'createdAt')::timestamp;
    NEW.updated_at := (NEW.revision_data->>'updatedAt')::timestamp;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER wr_fill_ts
    BEFORE INSERT OR UPDATE ON workflow_revisions
    FOR EACH ROW
EXECUTE FUNCTION workflow_revisions_fill_timestamps();
--comment: Create partial index for active revisions
CREATE INDEX idx_workflow_active ON workflow_revisions(namespace, id, active)
WHERE active = TRUE;
--comment: Create namespace index
CREATE INDEX idx_workflow_namespace ON workflow_revisions(namespace);
--comment: Create created_at descending index
CREATE INDEX idx_workflow_created_at ON workflow_revisions(created_at DESC);
--comment: Create GIN index on JSONB revision_data column
CREATE INDEX idx_workflow_revision_data_gin
ON workflow_revisions USING GIN(revision_data jsonb_path_ops);
--comment: Add table and column comments
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

--rollback DROP INDEX IF EXISTS idx_workflow_active;
--rollback DROP INDEX IF EXISTS idx_workflow_namespace;
--rollback DROP INDEX IF EXISTS idx_workflow_created_at;
--rollback DROP INDEX IF EXISTS idx_workflow_revision_data_gin;
--rollback DROP TABLE workflow_revisions;