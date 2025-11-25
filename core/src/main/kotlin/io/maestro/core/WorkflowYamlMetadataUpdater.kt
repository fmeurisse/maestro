package io.maestro.core

import java.time.Instant

/**
 * Utility for updating metadata fields (version, createdAt, updatedAt) in YAML source.
 * Uses regexp-based approach to preserve formatting and comments while updating/adding fields.
 *
 * This utility handles both scenarios:
 * - Field exists: Updates the value in place
 * - Field doesn't exist: Adds the field at the appropriate location
 *
 * The fields are added/updated at the top level of the YAML document, after namespace and id.
 */
class WorkflowYamlMetadataUpdater {

    companion object {
        // Regex patterns for matching existing fields
        private val VERSION_PATTERN = Regex("""^(version\s*:\s*).*$""", RegexOption.MULTILINE)
        private val CREATED_AT_PATTERN = Regex("""^(createdAt\s*:\s*).*$""", RegexOption.MULTILINE)
        private val UPDATED_AT_PATTERN = Regex("""^(updatedAt\s*:\s*).*$""", RegexOption.MULTILINE)
        private val ACTIVE_PATTERN = Regex("""^(active\s*:\s*).*$""", RegexOption.MULTILINE)

        // Value-capturing patterns (used for extraction)
        private val UPDATED_AT_VALUE_PATTERN = Regex("""^updatedAt\s*:\s*(.+)$""", RegexOption.MULTILINE)

        // Pattern to find location after namespace and id for inserting fields
        private val AFTER_ID_PATTERN = Regex("""(^namespace\s*:.*\n)(id\s*:.*\n)""", RegexOption.MULTILINE)

        /**
         * Updates or adds version field in the YAML source.
         *
         * @param yamlSource Original YAML source
         * @param version Version number to set
         * @return Updated YAML source with version field
         */
        fun updateVersion(yamlSource: String, version: Int): String {
            return if (VERSION_PATTERN.containsMatchIn(yamlSource)) {
                // Update existing version field
                yamlSource.replace(VERSION_PATTERN, "$1$version")
            } else {
                // Add version field after id
                insertFieldAfterID(yamlSource, "version", version.toString())
            }
        }

        /**
         * Updates or adds createdAt field in the YAML source.
         *
         * @param yamlSource Original YAML source
         * @param createdAt Timestamp to set (ISO-8601 format)
         * @return Updated YAML source with createdAt field
         */
        fun updateCreatedAt(yamlSource: String, createdAt: Instant): String {
            val formattedTimestamp = createdAt.toString()
            return if (CREATED_AT_PATTERN.containsMatchIn(yamlSource)) {
                // Update existing createdAt field
                yamlSource.replace(CREATED_AT_PATTERN, "$1$formattedTimestamp")
            } else {
                // Add createdAt field after version (or after id if version doesn't exist)
                insertFieldAfterVersion(yamlSource, "createdAt", formattedTimestamp)
            }
        }

        /**
         * Updates or adds updatedAt field in the YAML source.
         *
         * @param yamlSource Original YAML source
         * @param updatedAt Timestamp to set (ISO-8601 format)
         * @return Updated YAML source with updatedAt field
         */
        fun updateUpdatedAt(yamlSource: String, updatedAt: Instant): String {
            val formattedTimestamp = updatedAt.toString()
            return if (UPDATED_AT_PATTERN.containsMatchIn(yamlSource)) {
                // Update existing updatedAt field
                yamlSource.replace(UPDATED_AT_PATTERN, "$1$formattedTimestamp")
            } else {
                // Add updatedAt field after createdAt (or after version/id if createdAt doesn't exist)
                insertFieldAfterCreatedAt(yamlSource, "updatedAt", formattedTimestamp)
            }
        }

        /**
         * Updates all metadata fields (version, createdAt, updatedAt) in the YAML source.
         * This is the primary method for creating new revisions.
         *
         * @param yamlSource Original YAML source
         * @param version Version number to set
         * @param createdAt Creation timestamp
         * @param updatedAt Update timestamp
         * @return Updated YAML source with all metadata fields
         */
        fun updateAllMetadata(
            yamlSource: String,
            version: Int,
            createdAt: Instant,
            updatedAt: Instant
        ): String {
            var updated = updateVersion(yamlSource, version)
            updated = updateCreatedAt(updated, createdAt)
            updated = updateUpdatedAt(updated, updatedAt)
            return updated
        }

        /**
         * Updates only the updatedAt field, preserving existing version and createdAt.
         * This is used for activate/deactivate/update operations.
         *
         * @param yamlSource Original YAML source
         * @param updatedAt Update timestamp
         * @return Updated YAML source with updated updatedAt field
         */
        fun updateTimestamp(yamlSource: String, updatedAt: Instant): String {
            return updateUpdatedAt(yamlSource, updatedAt)
        }

        /**
         * Updates or adds the active flag in the YAML source.
         * If absent, the field is inserted after updatedAt when present,
         * otherwise after createdAt/version/id in that order.
         */
        fun updateActive(yamlSource: String, active: Boolean): String {
            val activeValue = active.toString()
            return if (ACTIVE_PATTERN.containsMatchIn(yamlSource)) {
                yamlSource.replace(ACTIVE_PATTERN, "$1$activeValue")
            } else {
                insertFieldAfterUpdatedAt(yamlSource, "active", activeValue)
            }
        }

        /**
         * Extracts the updatedAt timestamp from the YAML source, if present.
         * Returns null when the field is missing or cannot be parsed as an Instant.
         */
        fun extractUpdatedAt(yamlSource: String): Instant? {
            val match = UPDATED_AT_VALUE_PATTERN.find(yamlSource) ?: return null
            val raw = match.groupValues.getOrNull(1)?.trim() ?: return null
            return try {
                Instant.parse(raw)
            } catch (_: Exception) {
                null
            }
        }

        /**
         * Extracts the updatedAt timestamp or throws IllegalArgumentException if absent or invalid.
         */
        fun requireUpdatedAt(yamlSource: String): Instant {
            return extractUpdatedAt(yamlSource)
                ?: throw IllegalArgumentException("updatedAt not found or invalid in YAML source")
        }

        // Note: No legacy aliases are kept to encourage direct usage of requireUpdatedAt/extractUpdatedAt

        /**
         * Inserts a field after the "id" field.
         * Used when the field doesn't exist and needs to be added at the top level.
         */
        private fun insertFieldAfterID(yamlSource: String, fieldName: String, value: String): String {
            return if (AFTER_ID_PATTERN.containsMatchIn(yamlSource)) {
                yamlSource.replace(AFTER_ID_PATTERN, "$1$2$fieldName: $value\n")
            } else {
                // Fallback: just append at the end (shouldn't normally happen with valid YAML)
                "$yamlSource\n$fieldName: $value"
            }
        }

        /**
         * Inserts a field after the "version" field, or after "id" if version doesn't exist.
         */
        private fun insertFieldAfterVersion(yamlSource: String, fieldName: String, value: String): String {
            val versionPattern = Regex("""(^version\s*:.*\n)""", RegexOption.MULTILINE)
            return if (versionPattern.containsMatchIn(yamlSource)) {
                yamlSource.replace(versionPattern, "$1$fieldName: $value\n")
            } else {
                insertFieldAfterID(yamlSource, fieldName, value)
            }
        }

        /**
         * Inserts a field after the "createdAt" field, or after "version"/"id" if createdAt doesn't exist.
         */
        private fun insertFieldAfterCreatedAt(yamlSource: String, fieldName: String, value: String): String {
            val createdAtPattern = Regex("""(^createdAt\s*:.*\n)""", RegexOption.MULTILINE)
            return if (createdAtPattern.containsMatchIn(yamlSource)) {
                yamlSource.replace(createdAtPattern, "$1$fieldName: $value\n")
            } else {
                insertFieldAfterVersion(yamlSource, fieldName, value)
            }
        }

        /**
         * Inserts a field after the "updatedAt" field, or falls back to createdAt/version/id if not present.
         */
        private fun insertFieldAfterUpdatedAt(yamlSource: String, fieldName: String, value: String): String {
            val updatedAtPattern = Regex("""(^updatedAt\s*:.*\n)""", RegexOption.MULTILINE)
            return if (updatedAtPattern.containsMatchIn(yamlSource)) {
                yamlSource.replace(updatedAtPattern, "$1$fieldName: $value\n")
            } else {
                insertFieldAfterCreatedAt(yamlSource, fieldName, value)
            }
        }
    }
}
