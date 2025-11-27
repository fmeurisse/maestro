package io.maestro.api

import jakarta.inject.Inject
import org.jdbi.v3.core.Jdbi
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

abstract class AbstractAPIContractTest {

    @Inject
    lateinit var jdbi: Jdbi

    protected fun cleanupDatabase() {
        // Delete all workflow revisions before each test to ensure test isolation
        jdbi.useHandle<Exception> { handle ->
            handle.execute("DELETE FROM execution_step_results")
            handle.execute("DELETE FROM workflow_executions")
            handle.execute("DELETE FROM workflow_revisions")
        }
    }

    @BeforeEach
    fun cleanupBeforeTest() {
        cleanupDatabase()
    }

    @AfterEach
    fun cleanupAfterTest() {
        cleanupDatabase()
    }

}