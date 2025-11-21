package io.maestro.model.workflow

import io.maestro.model.WorkflowRevision
import io.maestro.model.WorkflowRevisionWithSource
import io.maestro.model.exception.InvalidWorkflowRevision
import io.maestro.model.steps.Step
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class WorkflowRevisionWithSourceTest {

    private val mockStep = object : Step {}
    private val yamlSource = """
        namespace: production
        id: workflow-1
        name: Test Workflow
        description: Test
        steps:
          type: Sequence
          steps: []
    """.trimIndent()

    @Test
    fun `create valid workflow revision with source succeeds`() {
        val revisionWithSource = WorkflowRevisionWithSource.create(
            namespace = "production",
            id = "workflow-1",
            version = 1,
            name = "Test",
            description = "Test",
            yamlSource = yamlSource,
            steps = mockStep
        )

        assertEquals("production", revisionWithSource.namespace)
        assertEquals("workflow-1", revisionWithSource.id)
        assertEquals(1, revisionWithSource.version)
        assertEquals("Test", revisionWithSource.name)
        assertEquals(yamlSource, revisionWithSource.yamlSource)
        assertFalse(revisionWithSource.active)
    }

    @Test
    fun `create with blank yaml source throws InvalidWorkflowRevision`() {
        val exception = assertThrows<InvalidWorkflowRevision> {
            WorkflowRevisionWithSource.create(
                namespace = "production",
                id = "workflow-1",
                version = 1,
                name = "Test",
                description = "Test",
                yamlSource = "",
                steps = mockStep
            )
        }
        assertEquals("YAML source must not be blank", exception.message)
    }

    @Test
    fun `create with whitespace-only yaml source throws InvalidWorkflowRevision`() {
        val exception = assertThrows<InvalidWorkflowRevision> {
            WorkflowRevisionWithSource.create(
                namespace = "production",
                id = "workflow-1",
                version = 1,
                name = "Test",
                description = "Test",
                yamlSource = "   \n  \t  ",
                steps = mockStep
            )
        }
        assertEquals("YAML source must not be blank", exception.message)
    }

    @Test
    fun `fromRevision creates instance from existing revision`() {
        val revision = WorkflowRevision.create(
            namespace = "production",
            id = "workflow-1",
            version = 1,
            name = "Test",
            description = "Test",
            steps = mockStep
        )

        val withSource = WorkflowRevisionWithSource.fromRevision(revision, yamlSource)

        assertEquals(revision.namespace, withSource.namespace)
        assertEquals(revision.version, withSource.version)
        assertEquals(yamlSource, withSource.yamlSource)
    }

    @Test
    fun `fromRevision with blank yaml source throws InvalidWorkflowRevision`() {
        val revision = WorkflowRevision.create(
            namespace = "production",
            id = "workflow-1",
            version = 1,
            name = "Test",
            description = "Test",
            steps = mockStep
        )

        val exception = assertThrows<InvalidWorkflowRevision> {
            WorkflowRevisionWithSource.fromRevision(revision, "")
        }
        assertEquals("YAML source must not be blank", exception.message)
    }

    @Test
    fun `toRevision drops yaml source`() {
        val withSource = WorkflowRevisionWithSource.create(
            namespace = "production",
            id = "workflow-1",
            version = 1,
            name = "Test",
            description = "Test",
            yamlSource = yamlSource,
            steps = mockStep
        )

        val revision = withSource.toRevision()

        assertEquals(withSource.namespace, revision.namespace)
        assertEquals(withSource.version, revision.version)
        assertEquals(withSource.name, revision.name)
        assertEquals(withSource.description, revision.description)
        assertEquals(withSource.active, revision.active)
    }

    @Test
    fun `convenience accessors delegate to revision`() {
        val withSource = WorkflowRevisionWithSource.create(
            namespace = "production",
            id = "workflow-1",
            version = 2,
            name = "Test Workflow",
            description = "Test Description",
            yamlSource = yamlSource,
            steps = mockStep,
            active = true
        )

        assertEquals("production", withSource.namespace)
        assertEquals("workflow-1", withSource.id)
        assertEquals(2, withSource.version)
        assertEquals("Test Workflow", withSource.name)
        assertEquals("Test Description", withSource.description)
        assertTrue(withSource.active)
        assertEquals(mockStep, withSource.steps)
    }

    @Test
    fun `activate updates revision and preserves yaml source`() {
        val withSource = WorkflowRevisionWithSource.create(
            namespace = "production",
            id = "workflow-1",
            version = 1,
            name = "Test",
            description = "Test",
            yamlSource = yamlSource,
            steps = mockStep,
            active = false
        )

        val activated = withSource.activate()

        assertTrue(activated.active)
        assertEquals(yamlSource, activated.yamlSource)
        assertNotEquals(withSource.updatedAt, activated.updatedAt)
    }

    @Test
    fun `deactivate updates revision and preserves yaml source`() {
        val withSource = WorkflowRevisionWithSource.create(
            namespace = "production",
            id = "workflow-1",
            version = 1,
            name = "Test",
            description = "Test",
            yamlSource = yamlSource,
            steps = mockStep,
            active = true
        )

        val deactivated = withSource.deactivate()

        assertFalse(deactivated.active)
        assertEquals(yamlSource, deactivated.yamlSource)
    }

    @Test
    fun `updateContent updates yaml source and steps`() {
        val withSource = WorkflowRevisionWithSource.create(
            namespace = "production",
            id = "workflow-1",
            version = 1,
            name = "Test",
            description = "Original description",
            yamlSource = yamlSource,
            steps = mockStep
        )

        val newStep = object : Step {}
        val newYaml = "namespace: production\nid: workflow-1\nupdated: true"
        val updated = withSource.updateContent(newYaml, newStep, "New description")

        assertEquals(newYaml, updated.yamlSource)
        assertEquals(newStep, updated.steps)
        assertEquals("New description", updated.description)
        assertNotEquals(withSource.updatedAt, updated.updatedAt)
    }

    @Test
    fun `updateContent with null description preserves original`() {
        val withSource = WorkflowRevisionWithSource.create(
            namespace = "production",
            id = "workflow-1",
            version = 1,
            name = "Test",
            description = "Original description",
            yamlSource = yamlSource,
            steps = mockStep
        )

        val newStep = object : Step {}
        val newYaml = "updated yaml"
        val updated = withSource.updateContent(newYaml, newStep, null)

        assertEquals("Original description", updated.description)
        assertEquals(newYaml, updated.yamlSource)
    }

    @Test
    fun `updateContent with blank yaml source throws InvalidWorkflowRevision`() {
        val withSource = WorkflowRevisionWithSource.create(
            namespace = "production",
            id = "workflow-1",
            version = 1,
            name = "Test",
            description = "Test",
            yamlSource = yamlSource,
            steps = mockStep
        )

        val exception = assertThrows<InvalidWorkflowRevision> {
            withSource.updateContent("", mockStep)
        }
        assertEquals("YAML source must not be blank", exception.message)
    }

    @Test
    fun `revisionId returns correct composite identifier`() {
        val withSource = WorkflowRevisionWithSource.create(
            namespace = "production",
            id = "workflow-1",
            version = 3,
            name = "Test",
            description = "Test",
            yamlSource = yamlSource,
            steps = mockStep
        )

        val revisionId = withSource.revisionId()

        assertEquals("production", revisionId.namespace)
        assertEquals("workflow-1", revisionId.id)
        assertEquals(3, revisionId.version)
    }

    @Test
    fun `workflowId returns identifier without version`() {
        val withSource = WorkflowRevisionWithSource.create(
            namespace = "staging",
            id = "test-workflow",
            version = 1,
            name = "Test",
            description = "Test",
            yamlSource = yamlSource,
            steps = mockStep
        )

        val workflowId = withSource.workflowId()

        assertEquals("staging", workflowId.namespace)
        assertEquals("test-workflow", workflowId.id)
    }

    @Test
    fun `yaml source preserves multiline content`() {
        val multilineYaml = """
            namespace: production
            id: complex-workflow
            name: Complex Workflow
            description: |
              This is a multiline description
              with multiple lines
              and indentation
            steps:
              type: Sequence
              steps:
                - type: LogTask
                  message: "Step 1"
                - type: LogTask
                  message: "Step 2"
        """.trimIndent()

        val withSource = WorkflowRevisionWithSource.create(
            namespace = "production",
            id = "complex-workflow",
            version = 1,
            name = "Complex Workflow",
            description = "Multiline description",
            yamlSource = multilineYaml,
            steps = mockStep
        )

        assertEquals(multilineYaml, withSource.yamlSource)
    }

    @Test
    fun `validation errors from revision creation are propagated`() {
        val exception = assertThrows<InvalidWorkflowRevision> {
            WorkflowRevisionWithSource.create(
                namespace = "",  // Invalid namespace
                id = "workflow-1",
                version = 1,
                name = "Test",
                description = "Test",
                yamlSource = yamlSource,
                steps = mockStep
            )
        }
        assertEquals("Namespace must not be blank", exception.message)
    }
}
