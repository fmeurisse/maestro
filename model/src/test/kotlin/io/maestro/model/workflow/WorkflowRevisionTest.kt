package io.maestro.model.workflow

import io.maestro.model.WorkflowRevision
import io.maestro.model.exception.InvalidWorkflowRevision
import io.maestro.model.steps.Step
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class WorkflowRevisionTest {

    private val mockStep = object : Step {}
    private val now = Instant.now()

    @Test
    fun `create valid workflow revision succeeds`() {
        val revision = WorkflowRevision.create(
            namespace = "production",
            id = "payment-workflow",
            version = 1,
            name = "Payment Processing",
            description = "Handles payment processing",
            steps = mockStep,
            active = false,
            createdAt = now,
            updatedAt = now
        )

        assertEquals("production", revision.namespace)
        assertEquals("payment-workflow", revision.id)
        assertEquals(1, revision.version)
        assertEquals("Payment Processing", revision.name)
        assertEquals("Handles payment processing", revision.description)
        assertFalse(revision.active)
        assertEquals(now, revision.createdAt)
        assertEquals(now, revision.updatedAt)
    }

    @Test
    fun `create with blank namespace throws InvalidWorkflowRevision`() {
        val exception = assertThrows<InvalidWorkflowRevision> {
            WorkflowRevision.create(
                namespace = "",
                id = "workflow-1",
                version = 1,
                name = "Test",
                description = "Test",
                steps = mockStep
            )
        }
        assertEquals("Namespace must not be blank", exception.message)
    }

    @Test
    fun `create with blank id throws InvalidWorkflowRevision`() {
        val exception = assertThrows<InvalidWorkflowRevision> {
            WorkflowRevision.create(
                namespace = "production",
                id = "",
                version = 1,
                name = "Test",
                description = "Test",
                steps = mockStep
            )
        }
        assertEquals("ID must not be blank", exception.message)
    }

    @Test
    fun `create with invalid namespace format throws InvalidWorkflowRevision`() {
        val exception = assertThrows<InvalidWorkflowRevision> {
            WorkflowRevision.create(
                namespace = "invalid namespace!",
                id = "workflow-1",
                version = 1,
                name = "Test",
                description = "Test",
                steps = mockStep
            )
        }
        assertTrue(exception.message!!.contains("alphanumeric"))
    }

    @Test
    fun `create with invalid id format throws InvalidWorkflowRevision`() {
        val exception = assertThrows<InvalidWorkflowRevision> {
            WorkflowRevision.create(
                namespace = "production",
                id = "invalid id!",
                version = 1,
                name = "Test",
                description = "Test",
                steps = mockStep
            )
        }
        assertTrue(exception.message!!.contains("alphanumeric"))
    }

    @Test
    fun `create with zero version throws InvalidWorkflowRevision`() {
        val exception = assertThrows<InvalidWorkflowRevision> {
            WorkflowRevision.create(
                namespace = "production",
                id = "workflow-1",
                version = 0,
                name = "Test",
                description = "Test",
                steps = mockStep
            )
        }
        assertEquals("Version must be positive", exception.message)
    }

    @Test
    fun `create with negative version throws InvalidWorkflowRevision`() {
        val exception = assertThrows<InvalidWorkflowRevision> {
            WorkflowRevision.create(
                namespace = "production",
                id = "workflow-1",
                version = -1,
                name = "Test",
                description = "Test",
                steps = mockStep
            )
        }
        assertEquals("Version must be positive", exception.message)
    }

    @Test
    fun `create with blank name throws InvalidWorkflowRevision`() {
        val exception = assertThrows<InvalidWorkflowRevision> {
            WorkflowRevision.create(
                namespace = "production",
                id = "workflow-1",
                version = 1,
                name = "",
                description = "Test",
                steps = mockStep
            )
        }
        assertEquals("Name must not be blank", exception.message)
    }

    @Test
    fun `create with too long namespace throws InvalidWorkflowRevision`() {
        val exception = assertThrows<InvalidWorkflowRevision> {
            WorkflowRevision.create(
                namespace = "a".repeat(101),
                id = "workflow-1",
                version = 1,
                name = "Test",
                description = "Test",
                steps = mockStep
            )
        }
        assertTrue(exception.message!!.contains("100 characters"))
    }

    @Test
    fun `create with too long id throws InvalidWorkflowRevision`() {
        val exception = assertThrows<InvalidWorkflowRevision> {
            WorkflowRevision.create(
                namespace = "production",
                id = "a".repeat(101),
                version = 1,
                name = "Test",
                description = "Test",
                steps = mockStep
            )
        }
        assertTrue(exception.message!!.contains("100 characters"))
    }

    @Test
    fun `create with too long name throws InvalidWorkflowRevision`() {
        val exception = assertThrows<InvalidWorkflowRevision> {
            WorkflowRevision.create(
                namespace = "production",
                id = "workflow-1",
                version = 1,
                name = "a".repeat(256),
                description = "Test",
                steps = mockStep
            )
        }
        assertTrue(exception.message!!.contains("255 characters"))
    }

    @Test
    fun `create with too long description throws InvalidWorkflowRevision`() {
        val exception = assertThrows<InvalidWorkflowRevision> {
            WorkflowRevision.create(
                namespace = "production",
                id = "workflow-1",
                version = 1,
                name = "Test",
                description = "a".repeat(1001),
                steps = mockStep
            )
        }
        assertTrue(exception.message!!.contains("1000 characters"))
    }

    @Test
    fun `create with maximum length description succeeds`() {
        val revision = WorkflowRevision.create(
            namespace = "production",
            id = "workflow-1",
            version = 1,
            name = "Test",
            description = "a".repeat(1000),
            steps = mockStep
        )
        assertEquals(1000, revision.description.length)
    }

    @Test
    fun `activate changes active flag and updates timestamp`() {
        val revision = WorkflowRevision.create(
            namespace = "production",
            id = "workflow-1",
            version = 1,
            name = "Test",
            description = "Test",
            steps = mockStep,
            active = false,
            createdAt = now,
            updatedAt = now
        )

        val activated = revision.activate()

        assertTrue(activated.active)
        assertNotEquals(now, activated.updatedAt)
        assertEquals(revision.namespace, activated.namespace)
        assertEquals(revision.version, activated.version)
    }

    @Test
    fun `deactivate changes active flag and updates timestamp`() {
        val revision = WorkflowRevision.create(
            namespace = "production",
            id = "workflow-1",
            version = 1,
            name = "Test",
            description = "Test",
            steps = mockStep,
            active = true,
            createdAt = now,
            updatedAt = now
        )

        val deactivated = revision.deactivate()

        assertFalse(deactivated.active)
        assertNotEquals(now, deactivated.updatedAt)
    }

    @Test
    fun `withUpdatedTimestamp updates only timestamp`() {
        val revision = WorkflowRevision.create(
            namespace = "production",
            id = "workflow-1",
            version = 1,
            name = "Test",
            description = "Test",
            steps = mockStep,
            createdAt = now,
            updatedAt = now
        )

        val updated = revision.withUpdatedTimestamp()

        assertNotEquals(now, updated.updatedAt)
        assertEquals(revision.namespace, updated.namespace)
        assertEquals(revision.active, updated.active)
    }

    @Test
    fun `revisionId returns correct composite identifier`() {
        val revision = WorkflowRevision.create(
            namespace = "production",
            id = "workflow-1",
            version = 2,
            name = "Test",
            description = "Test",
            steps = mockStep
        )

        val revisionId = revision.revisionId()

        assertEquals("production", revisionId.namespace)
        assertEquals("workflow-1", revisionId.id)
        assertEquals(2, revisionId.version)
    }

    @Test
    fun `workflowId returns identifier without version`() {
        val revision = WorkflowRevision.create(
            namespace = "production",
            id = "workflow-1",
            version = 2,
            name = "Test",
            description = "Test",
            steps = mockStep
        )

        val workflowId = revision.workflowId()

        assertEquals("production", workflowId.namespace)
        assertEquals("workflow-1", workflowId.id)
    }

    @Test
    fun `namespace with hyphens and underscores is valid`() {
        val revision = WorkflowRevision.create(
            namespace = "prod-staging_test",
            id = "workflow-1",
            version = 1,
            name = "Test",
            description = "Test",
            steps = mockStep
        )
        assertEquals("prod-staging_test", revision.namespace)
    }

    @Test
    fun `id with hyphens and underscores is valid`() {
        val revision = WorkflowRevision.create(
            namespace = "production",
            id = "payment_processing-v2",
            version = 1,
            name = "Test",
            description = "Test",
            steps = mockStep
        )
        assertEquals("payment_processing-v2", revision.id)
    }
}
