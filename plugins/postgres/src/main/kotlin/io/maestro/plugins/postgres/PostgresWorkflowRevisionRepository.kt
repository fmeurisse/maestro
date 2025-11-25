package io.maestro.plugins.postgres

import io.maestro.core.WorkflowJsonParser
import io.maestro.core.WorkflowYamlMetadataUpdater
import io.maestro.core.errors.ActiveRevisionConflictException
import io.maestro.core.errors.WorkflowAlreadyExistsException
import io.maestro.core.errors.WorkflowRevisionNotFoundException
import io.maestro.core.IWorkflowRevisionRepository
import io.maestro.model.WorkflowID
import io.maestro.model.WorkflowRevision
import io.maestro.model.WorkflowRevisionID
import io.maestro.model.WorkflowRevisionWithSource
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jdbi.v3.core.Jdbi

/**
 * PostgreSQL implementation of IWorkflowRevisionRepository.
 * 
 * Uses dual storage pattern:
 * - yaml_source TEXT: Preserves original YAML with formatting/comments
 * - revision_data JSONB: Stores complete WorkflowRevision (without yamlSource field)
 * 
 * Computed columns (namespace, id, version, etc.) are automatically generated
 * from revision_data JSONB, enabling efficient querying and indexing.
 */
@ApplicationScoped
class PostgresWorkflowRevisionRepository @Inject constructor(
    private val jdbi: Jdbi,
    private val jsonParser: WorkflowJsonParser
) : IWorkflowRevisionRepository {

    private val logger = KotlinLogging.logger {}

    // ===== Methods with YAML source (WorkflowRevisionWithSource) =====

    override fun saveWithSource(revision: WorkflowRevisionWithSource): WorkflowRevisionWithSource {
        logger.debug { "Saving workflow revision with source: ${revision.revisionId()}" }

        return jdbi.withHandle<WorkflowRevisionWithSource, Exception> { handle ->
            // Check if revision already exists
            val exists = handle.createQuery("""
                SELECT COUNT(*) FROM workflow_revisions
                WHERE namespace = :namespace AND id = :id AND version = :version
            """.trimIndent())
                .bind("namespace", revision.namespace)
                .bind("id", revision.id)
                .bind("version", revision.version)
                .mapTo(Int::class.java)
                .one()

            if (exists > 0) {
                throw WorkflowAlreadyExistsException(revision.revisionId())
            }

            // Serialize WorkflowRevision (without yamlSource) to JSONB
            val revisionJson = jsonParser.toJson(revision.revision)

            // Insert with dual storage
            handle.createUpdate("""
                INSERT INTO workflow_revisions (yaml_source, revision_data)
                VALUES (:yamlSource, :revisionData::jsonb)
            """.trimIndent())
                .bind("yamlSource", revision.yamlSource)
                .bind("revisionData", revisionJson)
                .execute()

            logger.debug { "Successfully saved workflow revision: ${revision.revisionId()}" }
            revision
        }
    }

    override fun updateWithSource(revision: WorkflowRevisionWithSource): WorkflowRevisionWithSource {
        logger.debug { "Updating workflow revision with source: ${revision.revisionId()}" }

        return jdbi.withHandle<WorkflowRevisionWithSource, Exception> { handle ->
            // Check if revision exists and is inactive
            val current = handle.createQuery("""
                SELECT active FROM workflow_revisions
                WHERE namespace = :namespace AND id = :id AND version = :version
            """.trimIndent())
                .bind("namespace", revision.namespace)
                .bind("id", revision.id)
                .bind("version", revision.version)
                .mapTo(Boolean::class.java)
                .findFirst()
                .orElse(null)

            if (current == null) {
                throw WorkflowRevisionNotFoundException(revision.revisionId())
            }
            if (current) {
                throw ActiveRevisionConflictException(revision.revisionId(), "update")
            }

            // Serialize updated WorkflowRevision to JSONB
            val revisionJson = jsonParser.toJson(revision.revision)

            // Update with dual storage
            val rowsUpdated = handle.createUpdate("""
                UPDATE workflow_revisions
                SET yaml_source = :yamlSource,
                    revision_data = :revisionData::jsonb
                WHERE namespace = :namespace AND id = :id AND version = :version
            """.trimIndent())
                .bind("yamlSource", revision.yamlSource)
                .bind("revisionData", revisionJson)
                .bind("namespace", revision.namespace)
                .bind("id", revision.id)
                .bind("version", revision.version)
                .execute()

            if (rowsUpdated == 0) {
                throw WorkflowRevisionNotFoundException(revision.revisionId())
            }

            logger.debug { "Successfully updated workflow revision: ${revision.revisionId()}" }
            revision
        }
    }

    override fun findByIdWithSource(id: WorkflowRevisionID): WorkflowRevisionWithSource? {
        logger.debug { "Finding workflow revision with source: $id" }

        return jdbi.withHandle<WorkflowRevisionWithSource?, Exception> { handle ->
            handle.createQuery("""
                SELECT yaml_source, revision_data
                FROM workflow_revisions
                WHERE namespace = :namespace AND id = :id AND version = :version
            """.trimIndent())
                .bind("namespace", id.namespace)
                .bind("id", id.id)
                .bind("version", id.version)
                .map { rs, _ ->
                    val yamlSource = rs.getString("yaml_source")
                    val revisionJson = rs.getString("revision_data")
                    val revision = jsonParser.parseRevision(revisionJson, validate = false)
                    WorkflowRevisionWithSource.fromRevision(revision, yamlSource)
                }
                .findFirst()
                .orElse(null)
        }
    }

    // ===== Methods without YAML source (WorkflowRevision) =====

    override fun findById(id: WorkflowRevisionID): WorkflowRevision? {
        logger.debug { "Finding workflow revision: $id" }

        return jdbi.withHandle<WorkflowRevision?, Exception> { handle ->
            handle.createQuery("""
                SELECT revision_data
                FROM workflow_revisions
                WHERE namespace = :namespace AND id = :id AND version = :version
            """.trimIndent())
                .bind("namespace", id.namespace)
                .bind("id", id.id)
                .bind("version", id.version)
                .map { rs, _ ->
                    val revisionJson = rs.getString("revision_data")
                    jsonParser.parseRevision(revisionJson, validate = false)
                }
                .findFirst()
                .orElse(null)
        }
    }

    override fun findByWorkflowId(workflowId: WorkflowID): List<WorkflowRevision> {
        logger.debug { "Finding all revisions for workflow: $workflowId" }

        return jdbi.withHandle<List<WorkflowRevision>, Exception> { handle ->
            handle.createQuery("""
                SELECT revision_data
                FROM workflow_revisions
                WHERE namespace = :namespace AND id = :id
                ORDER BY version ASC
            """.trimIndent())
                .bind("namespace", workflowId.namespace)
                .bind("id", workflowId.id)
                .map { rs, _ ->
                    val revisionJson = rs.getString("revision_data")
                    jsonParser.parseRevision(revisionJson, validate = false)
                }
                .list()
        }
    }

    override fun findActiveRevisions(workflowId: WorkflowID): List<WorkflowRevision> {
        logger.debug { "Finding active revisions for workflow: $workflowId" }

        return jdbi.withHandle<List<WorkflowRevision>, Exception> { handle ->
            handle.createQuery("""
                SELECT revision_data
                FROM workflow_revisions
                WHERE namespace = :namespace AND id = :id AND active = TRUE
                ORDER BY version ASC
            """.trimIndent())
                .bind("namespace", workflowId.namespace)
                .bind("id", workflowId.id)
                .map { rs, _ ->
                    val revisionJson = rs.getString("revision_data")
                    jsonParser.parseRevision(revisionJson, validate = false)
                }
                .list()
        }
    }

    // ===== Utility methods =====

    override fun findMaxVersion(workflowId: WorkflowID): Int? {
        logger.debug { "Finding max version for workflow: $workflowId" }

        return jdbi.withHandle<Int?, Exception> { handle ->
            handle.createQuery("""
                SELECT MAX(version) as max_version
                FROM workflow_revisions
                WHERE namespace = :namespace AND id = :id
            """.trimIndent())
                .bind("namespace", workflowId.namespace)
                .bind("id", workflowId.id)
                .map { rs, _ ->
                    val version = rs.getObject("max_version")
                    when (version) {
                        is Number -> version.toInt()
                        else -> null
                    }
                }
                .findFirst()
                .orElse(null)
        }
    }

    override fun exists(workflowId: WorkflowID): Boolean {
        logger.debug { "Checking if workflow exists: $workflowId" }

        return jdbi.withHandle<Boolean, Exception> { handle ->
            val count = handle.createQuery("""
                SELECT COUNT(*) FROM workflow_revisions
                WHERE namespace = :namespace AND id = :id
            """.trimIndent())
                .bind("namespace", workflowId.namespace)
                .bind("id", workflowId.id)
                .mapTo(Int::class.java)
                .one()

            count > 0
        }
    }

    override fun deleteById(id: WorkflowRevisionID) {
        logger.debug { "Deleting workflow revision: $id" }

        jdbi.useHandle<Exception> { handle ->
            // Check if revision exists and is inactive
            val current = handle.createQuery("""
                SELECT active FROM workflow_revisions
                WHERE namespace = :namespace AND id = :id AND version = :version
            """.trimIndent())
                .bind("namespace", id.namespace)
                .bind("id", id.id)
                .bind("version", id.version)
                .mapTo(Boolean::class.java)
                .findFirst()
                .orElse(null)

            if (current == null) {
                throw WorkflowRevisionNotFoundException(id)
            }
            if (current) {
                throw ActiveRevisionConflictException(id, "delete")
            }

            val rowsDeleted = handle.createUpdate("""
                DELETE FROM workflow_revisions
                WHERE namespace = :namespace AND id = :id AND version = :version
            """.trimIndent())
                .bind("namespace", id.namespace)
                .bind("id", id.id)
                .bind("version", id.version)
                .execute()

            if (rowsDeleted == 0) {
                throw WorkflowRevisionNotFoundException(id)
            }

            logger.debug { "Successfully deleted workflow revision: $id" }
        }
    }

    override fun deleteByWorkflowId(workflowId: WorkflowID): Int {
        logger.debug { "Deleting all revisions for workflow: $workflowId" }

        return jdbi.withHandle<Int, Exception> { handle ->
            val rowsDeleted = handle.createUpdate("""
                DELETE FROM workflow_revisions
                WHERE namespace = :namespace AND id = :id
            """.trimIndent())
                .bind("namespace", workflowId.namespace)
                .bind("id", workflowId.id)
                .execute()

            logger.debug { "Deleted $rowsDeleted revisions for workflow: $workflowId" }
            rowsDeleted
        }
    }

    override fun listWorkflows(namespace: String): List<WorkflowID> {
        logger.debug { "Listing workflows in namespace: $namespace" }

        return jdbi.withHandle<List<WorkflowID>, Exception> { handle ->
            handle.createQuery("""
                SELECT DISTINCT namespace, id
                FROM workflow_revisions
                WHERE namespace = :namespace
                ORDER BY id ASC
            """.trimIndent())
                .bind("namespace", namespace)
                .map { rs, _ ->
                    WorkflowID(
                        namespace = rs.getString("namespace"),
                        id = rs.getString("id")
                    )
                }
                .list()
        }
    }

    override fun activateWithSource(id: WorkflowRevisionID, updatedYamlSource: String): WorkflowRevisionWithSource {
        logger.debug { "Activating workflow revision with source: $id" }

        return jdbi.withHandle<WorkflowRevisionWithSource, Exception> { handle ->
            // Get current revision data within the same handle
            val currentJson = handle.createQuery("""
                SELECT revision_data
                FROM workflow_revisions
                WHERE namespace = :namespace AND id = :id AND version = :version
            """.trimIndent())
                .bind("namespace", id.namespace)
                .bind("id", id.id)
                .bind("version", id.version)
                .map { rs, _ -> rs.getString("revision_data") }
                .findFirst()
                .orElse(null) ?: throw WorkflowRevisionNotFoundException(id)

            val current = jsonParser.parseRevision(currentJson, validate = false)

            // Extract the updatedAt timestamp from the YAML source to ensure consistency
            val updatedAt = WorkflowYamlMetadataUpdater.requireUpdatedAt(updatedYamlSource)

            // Enforce active=true in YAML regardless of input
            val enforcedYamlSource = WorkflowYamlMetadataUpdater.updateActive(updatedYamlSource, true)

            // Update active flag with the timestamp from the YAML
            val updatedRevision = current.activate(updatedAt)
            val revisionJson = jsonParser.toJson(updatedRevision)

            // Update both revision_data and yaml_source
            handle.createUpdate("""
                UPDATE workflow_revisions
                SET revision_data = :revisionData::jsonb,
                    yaml_source = :yamlSource
                WHERE namespace = :namespace AND id = :id AND version = :version
            """.trimIndent())
                .bind("revisionData", revisionJson)
                .bind("yamlSource", enforcedYamlSource)
                .bind("namespace", id.namespace)
                .bind("id", id.id)
                .bind("version", id.version)
                .execute()

            logger.debug { "Successfully activated workflow revision: $id" }
            WorkflowRevisionWithSource.fromRevision(updatedRevision, enforcedYamlSource)
        }
    }

    override fun deactivateWithSource(id: WorkflowRevisionID, updatedYamlSource: String): WorkflowRevisionWithSource {
        logger.debug { "Deactivating workflow revision with source: $id" }

        return jdbi.withHandle<WorkflowRevisionWithSource, Exception> { handle ->
            // Get current revision data within the same handle
            val currentJson = handle.createQuery("""
                SELECT revision_data
                FROM workflow_revisions
                WHERE namespace = :namespace AND id = :id AND version = :version
            """.trimIndent())
                .bind("namespace", id.namespace)
                .bind("id", id.id)
                .bind("version", id.version)
                .map { rs, _ -> rs.getString("revision_data") }
                .findFirst()
                .orElse(null) ?: throw WorkflowRevisionNotFoundException(id)

            val current = jsonParser.parseRevision(currentJson, validate = false)

            // Extract the updatedAt timestamp from the YAML source to ensure consistency
            val updatedAt = WorkflowYamlMetadataUpdater.requireUpdatedAt(updatedYamlSource)

            // Enforce active=false in YAML regardless of input
            val enforcedYamlSource = WorkflowYamlMetadataUpdater.updateActive(updatedYamlSource, false)

            // Update active flag with the timestamp from the YAML
            val updatedRevision = current.deactivate(updatedAt)
            val revisionJson = jsonParser.toJson(updatedRevision)

            // Update both revision_data and yaml_source
            handle.createUpdate("""
                UPDATE workflow_revisions
                SET revision_data = :revisionData::jsonb,
                    yaml_source = :yamlSource
                WHERE namespace = :namespace AND id = :id AND version = :version
            """.trimIndent())
                .bind("revisionData", revisionJson)
                .bind("yamlSource", enforcedYamlSource)
                .bind("namespace", id.namespace)
                .bind("id", id.id)
                .bind("version", id.version)
                .execute()

            logger.debug { "Successfully deactivated workflow revision: $id" }
            WorkflowRevisionWithSource.fromRevision(updatedRevision, enforcedYamlSource)
        }
    }

}
