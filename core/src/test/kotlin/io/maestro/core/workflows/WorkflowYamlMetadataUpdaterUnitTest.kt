package io.maestro.core.workflows

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.time.Instant

class WorkflowYamlMetadataUpdaterUnitTest : FeatureSpec({

    val testInstant = Instant.parse("2024-01-15T10:30:00Z")
    val updatedInstant = Instant.parse("2024-01-16T15:45:00Z")

    feature("updateVersion") {
        scenario("should add version field when it doesn't exist") {
            // Given
            val yaml = """
                namespace: test-namespace
                id: test-workflow
                name: Test Workflow
                description: A test workflow
                steps: []
            """.trimIndent()

            // When
            val result = WorkflowYamlMetadataUpdater.updateVersion(yaml, 1)

            // Then
            result shouldContain "version: 1"
            val versionIdx = result.indexOf("version: 1")
            val idIdx = result.indexOf("id: test-workflow")
            (versionIdx > idIdx) shouldBe true
        }

        scenario("should update existing version field") {
            // Given
            val yaml = """
                namespace: test-namespace
                id: test-workflow
                version: 1
                name: Test Workflow
                description: A test workflow
                steps: []
            """.trimIndent()

            // When
            val result = WorkflowYamlMetadataUpdater.updateVersion(yaml, 2)

            // Then
            result shouldContain "version: 2"
            result shouldNotContain "version: 1"
        }
    }

    feature("updateCreatedAt") {
        scenario("should add createdAt field when it doesn't exist") {
            // Given
            val yaml = """
                namespace: test-namespace
                id: test-workflow
                version: 1
                name: Test Workflow
                description: A test workflow
                steps: []
            """.trimIndent()

            // When
            val result = WorkflowYamlMetadataUpdater.updateCreatedAt(yaml, testInstant)

            // Then
            result shouldContain "createdAt: ${testInstant}"
        }

        scenario("should update existing createdAt field") {
            // Given
            val yaml = """
                namespace: test-namespace
                id: test-workflow
                version: 1
                createdAt: 2023-01-01T00:00:00Z
                name: Test Workflow
                description: A test workflow
                steps: []
            """.trimIndent()

            // When
            val result = WorkflowYamlMetadataUpdater.updateCreatedAt(yaml, testInstant)

            // Then
            result shouldContain "createdAt: ${testInstant}"
            result shouldNotContain "createdAt: 2023-01-01T00:00:00Z"
        }
    }

    feature("updateUpdatedAt") {
        scenario("should add updatedAt field when it doesn't exist") {
            // Given
            val yaml = """
                namespace: test-namespace
                id: test-workflow
                version: 1
                createdAt: ${testInstant}
                name: Test Workflow
                description: A test workflow
                steps: []
            """.trimIndent()

            // When
            val result = WorkflowYamlMetadataUpdater.updateUpdatedAt(yaml, updatedInstant)

            // Then
            result shouldContain "updatedAt: ${updatedInstant}"
        }

        scenario("should update existing updatedAt field") {
            // Given
            val yaml = """
                namespace: test-namespace
                id: test-workflow
                version: 1
                createdAt: ${testInstant}
                updatedAt: ${testInstant}
                name: Test Workflow
                description: A test workflow
                steps: []
            """.trimIndent()

            // When
            val result = WorkflowYamlMetadataUpdater.updateUpdatedAt(yaml, updatedInstant)

            // Then
            result shouldContain "updatedAt: ${updatedInstant}"
            result shouldContain "createdAt: ${testInstant}"
        }
    }

    feature("updateAllMetadata") {
        scenario("should add all metadata fields when they don't exist") {
            // Given
            val yaml = """
                namespace: test-namespace
                id: test-workflow
                name: Test Workflow
                description: A test workflow
                steps: []
            """.trimIndent()

            // When
            val result = WorkflowYamlMetadataUpdater.updateAllMetadata(
                yamlSource = yaml,
                version = 1,
                createdAt = testInstant,
                updatedAt = testInstant
            )

            // Then
            result shouldContain "version: 1"
            result shouldContain "createdAt: ${testInstant}"
            result shouldContain "updatedAt: ${testInstant}"

            // Verify order: namespace, id, version, createdAt, updatedAt, name, description
            val versionIdx = result.indexOf("version: 1")
            val createdAtIdx = result.indexOf("createdAt:")
            val updatedAtIdx = result.indexOf("updatedAt:")
            val nameIdx = result.indexOf("name:")

            (versionIdx < createdAtIdx) shouldBe true
            (createdAtIdx < updatedAtIdx) shouldBe true
            (updatedAtIdx < nameIdx) shouldBe true
        }

        scenario("should update all existing metadata fields") {
            // Given
            val yaml = """
                namespace: test-namespace
                id: test-workflow
                version: 1
                createdAt: 2023-01-01T00:00:00Z
                updatedAt: 2023-01-01T00:00:00Z
                name: Test Workflow
                description: A test workflow
                steps: []
            """.trimIndent()

            // When
            val result = WorkflowYamlMetadataUpdater.updateAllMetadata(
                yamlSource = yaml,
                version = 2,
                createdAt = testInstant,
                updatedAt = updatedInstant
            )

            // Then
            result shouldContain "version: 2"
            result shouldContain "createdAt: ${testInstant}"
            result shouldContain "updatedAt: ${updatedInstant}"

            result shouldNotContain "version: 1"
            result shouldNotContain "2023-01-01T00:00:00Z"
        }

        scenario("should work with minimal YAML") {
            // Given
            val yaml = """
                namespace: test
                id: workflow
                name: Test
                description: Test
                steps: []
            """.trimIndent()

            // When
            val result = WorkflowYamlMetadataUpdater.updateAllMetadata(
                yamlSource = yaml,
                version = 1,
                createdAt = testInstant,
                updatedAt = testInstant
            )

            // Then
            result shouldContain "namespace: test"
            result shouldContain "id: workflow"
            result shouldContain "version: 1"
            result shouldContain "createdAt: ${testInstant}"
            result shouldContain "updatedAt: ${testInstant}"
            result shouldContain "name: Test"
        }
    }

    feature("updateTimestamp") {
        scenario("should only update updatedAt field") {
            // Given
            val yaml = """
                namespace: test-namespace
                id: test-workflow
                version: 1
                createdAt: ${testInstant}
                updatedAt: ${testInstant}
                name: Test Workflow
                description: A test workflow
                steps: []
            """.trimIndent()

            // When
            val result = WorkflowYamlMetadataUpdater.updateTimestamp(yaml, updatedInstant)

            // Then
            result shouldContain "version: 1"
            result shouldContain "createdAt: ${testInstant}"
            result shouldContain "updatedAt: ${updatedInstant}"
        }

        scenario("should add updatedAt if it doesn't exist") {
            // Given
            val yaml = """
                namespace: test-namespace
                id: test-workflow
                version: 1
                createdAt: ${testInstant}
                name: Test Workflow
                description: A test workflow
                steps: []
            """.trimIndent()

            // When
            val result = WorkflowYamlMetadataUpdater.updateTimestamp(yaml, updatedInstant)

            // Then
            result shouldContain "updatedAt: ${updatedInstant}"
            result shouldContain "createdAt: ${testInstant}"
            result shouldContain "version: 1"
        }
    }

    feature("YAML formatting preservation") {
        scenario("should preserve YAML comments and formatting") {
            // Given
            val yaml = """
                # This is a test workflow
                namespace: test-namespace
                id: test-workflow
                # Version comment
                version: 1
                name: Test Workflow
                description: A test workflow
                steps: []
            """.trimIndent()

            // When
            val result = WorkflowYamlMetadataUpdater.updateVersion(yaml, 2)

            // Then
            result shouldContain "# This is a test workflow"
            result shouldContain "# Version comment"
            result shouldContain "version: 2"
        }

        scenario("should handle YAML with indented fields") {
            // Given
            val yaml = """
                namespace: test-namespace
                id: test-workflow
                  version: 1
                name: Test Workflow
                description: A test workflow
                steps: []
            """.trimIndent()

            // When
            val result = WorkflowYamlMetadataUpdater.updateVersion(yaml, 2)

            // Then
            result shouldContain "version: 2"
        }

        scenario("should handle YAML with extra spaces around colons") {
            // Given
            val yaml = """
                namespace : test-namespace
                id   :   test-workflow
                version  :  1
                name: Test Workflow
                description: A test workflow
                steps: []
            """.trimIndent()

            // When
            val result = WorkflowYamlMetadataUpdater.updateVersion(yaml, 2)

            // Then
            result shouldContain "version  :  2"
        }
    }

    feature("extractUpdatedAt / requireUpdatedAt") {
        scenario("extractUpdatedAt returns Instant when present and valid") {
            val ts = Instant.parse("2024-06-01T10:15:30Z")
            val yaml = """
                namespace: ns
                id: wf
                version: 3
                createdAt: 2024-05-01T09:00:00Z
                updatedAt: $ts
            """.trimIndent()

            val extracted = WorkflowYamlMetadataUpdater.extractUpdatedAt(yaml)
            extracted shouldBe ts
        }

        scenario("extractUpdatedAt returns null when missing or invalid") {
            val yamlMissing = """
                namespace: ns
                id: wf
                version: 1
            """.trimIndent()

            WorkflowYamlMetadataUpdater.extractUpdatedAt(yamlMissing).shouldBeNull()

            val yamlInvalid = """
                namespace: ns
                id: wf
                version: 1
                updatedAt: not-a-timestamp
            """.trimIndent()

            WorkflowYamlMetadataUpdater.extractUpdatedAt(yamlInvalid).shouldBeNull()
        }

        scenario("requireUpdatedAt throws when missing or invalid") {
            val yamlMissing = """
                namespace: ns
                id: wf
                version: 1
            """.trimIndent()

            shouldThrow<IllegalArgumentException> {
                WorkflowYamlMetadataUpdater.requireUpdatedAt(yamlMissing)
            }

            val yamlInvalid = """
                namespace: ns
                id: wf
                version: 1
                updatedAt: not-a-timestamp
            """.trimIndent()

            shouldThrow<IllegalArgumentException> {
                WorkflowYamlMetadataUpdater.requireUpdatedAt(yamlInvalid)
            }
        }
    }

    feature("updateActive behavior") {
        scenario("updates existing active flag from true to false and vice-versa") {
            val yamlTrue = """
                namespace: ns
                id: wf
                version: 2
                createdAt: 2024-05-01T09:00:00Z
                updatedAt: 2024-06-01T10:15:30Z
                active: true
            """.trimIndent()

            val updatedFalse = WorkflowYamlMetadataUpdater.updateActive(yamlTrue, false)
            updatedFalse.shouldContain("active: false")
            updatedFalse.shouldNotContain("active: true")

            val yamlFalse = yamlTrue.replace("active: true", "active: false")
            val updatedTrue = WorkflowYamlMetadataUpdater.updateActive(yamlFalse, true)
            updatedTrue.shouldContain("active: true")
            updatedTrue.shouldNotContain("active: false\n")
        }

        scenario("inserts active immediately after updatedAt when present") {
            val ts = "2024-06-01T10:15:30Z"
            val yaml = """
                namespace: ns
                id: wf
                version: 2
                createdAt: 2024-05-01T09:00:00Z
                updatedAt: $ts
                name: Sample
            """.trimIndent()

            val result = WorkflowYamlMetadataUpdater.updateActive(yaml, true)

            // The updater should insert "active" right after the updatedAt line
            val expectedSnippet = "updatedAt: $ts\nactive: true\n"
            result.shouldContain(expectedSnippet)
        }

        scenario("falls back to after createdAt when updatedAt is missing") {
            val yaml = """
                namespace: ns
                id: wf
                version: 2
                createdAt: 2024-05-01T09:00:00Z
                name: Sample
            """.trimIndent()

            val result = WorkflowYamlMetadataUpdater.updateActive(yaml, false)
            val expectedSnippet = "createdAt: 2024-05-01T09:00:00Z\nactive: false\n"
            result.shouldContain(expectedSnippet)
        }

        scenario("falls back to after version when updatedAt and createdAt are missing") {
            val yaml = """
                namespace: ns
                id: wf
                version: 5
                name: Sample
            """.trimIndent()

            val result = WorkflowYamlMetadataUpdater.updateActive(yaml, true)
            val expectedSnippet = "version: 5\nactive: true\n"
            result.shouldContain(expectedSnippet)
        }

        scenario("falls back to after id when updatedAt/createdAt/version are missing") {
            val yaml = """
                namespace: ns
                id: wf
                name: Sample
            """.trimIndent()

            val result = WorkflowYamlMetadataUpdater.updateActive(yaml, false)

            // Ensure that the active flag is present and appears after the id line
            val idIndex = result.indexOf("\nid: wf\n")
            val activeIndex = result.indexOf("\nactive: false\n")
            (idIndex >= 0 && activeIndex > idIndex).shouldBeTrue()
        }
    }
})
