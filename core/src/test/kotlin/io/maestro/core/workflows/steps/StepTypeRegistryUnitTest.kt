package io.maestro.core.workflows.steps

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.util.*

/**
 * Unit tests for StepTypeRegistry discovery mechanism.
 *
 * Tests verify that:
 * - ServiceLoader can discover StepTypesProvider implementations
 * - Test provider is discovered from test resources
 * - Core provider is discovered from main resources
 * - Registry can retrieve step classes by type name
 */
class StepTypeRegistryUnitTest : FeatureSpec({

    feature("ServiceLoader discovery") {
        scenario("should discover test provider from test resources") {
            // Load providers using ServiceLoader
            val providers = ServiceLoader.load(StepTypesProvider::class.java).toList()

            // Should discover at least the test provider
            providers.shouldNotBeEmpty()

            // Find the test provider
            val testProvider = providers.find { it is TestStepTypesProvider }
            testProvider.shouldNotBeNull()

            // Verify test provider returns correct step types
            val testProviderTyped = testProvider as TestStepTypesProvider
            val stepTypes = testProviderTyped.provideStepTypes()

            stepTypes shouldHaveSize 1
            stepTypes shouldContainKey TestTask.TYPE_NAME
            stepTypes[TestTask.TYPE_NAME] shouldBe TestTask::class
        }

        scenario("should discover core provider from main resources") {
            // Load providers using ServiceLoader
            val providers = ServiceLoader.load(StepTypesProvider::class.java).toList()

            // Find the core provider
            val coreProvider = providers.find { it is CoreStepTypesProvider }
            coreProvider.shouldNotBeNull()

            // Verify core provider returns correct step types
            val coreProviderTyped = coreProvider as CoreStepTypesProvider
            val stepTypes = coreProviderTyped.provideStepTypes()

            stepTypes shouldHaveSize 3
            stepTypes shouldContainKey Sequence.TYPE_NAME
            stepTypes shouldContainKey If.TYPE_NAME
            stepTypes shouldContainKey LogTask.TYPE_NAME

            stepTypes[Sequence.TYPE_NAME] shouldBe Sequence::class
            stepTypes[If.TYPE_NAME] shouldBe If::class
            stepTypes[LogTask.TYPE_NAME] shouldBe LogTask::class
        }
    }

    feature("StepTypeRegistry discovery") {
        scenario("should discover all providers including test provider") {
            // Access the registry (will trigger discovery)
            val registry = StepTypeRegistry

            // Verify core step types are registered
            registry.isRegistered(Sequence.TYPE_NAME) shouldBe true
            registry.isRegistered(If.TYPE_NAME) shouldBe true
            registry.isRegistered(LogTask.TYPE_NAME) shouldBe true

            // Verify test step type is registered (from test resources)
            registry.isRegistered(TestTask.TYPE_NAME) shouldBe true

            // Verify we can retrieve the classes
            registry.getStepClass(Sequence.TYPE_NAME) shouldBe Sequence::class
            registry.getStepClass(If.TYPE_NAME) shouldBe If::class
            registry.getStepClass(LogTask.TYPE_NAME) shouldBe LogTask::class
            registry.getStepClass(TestTask.TYPE_NAME) shouldBe TestTask::class
        }

        scenario("getAllTypeNames should include all discovered types") {
            val registry = StepTypeRegistry
            val allTypeNames = registry.getAllTypeNames()

            // Should include core types
            allTypeNames shouldContain Sequence.TYPE_NAME
            allTypeNames shouldContain If.TYPE_NAME
            allTypeNames shouldContain LogTask.TYPE_NAME

            // Should include test type
            allTypeNames shouldContain TestTask.TYPE_NAME

            // Should have at least 4 types (3 core + 1 test)
            allTypeNames.size shouldBeGreaterThanOrEqual 4
        }

        scenario("getStepClass should return null for unknown type") {
            val registry = StepTypeRegistry
            val unknownClass = registry.getStepClass("UnknownStepType")

            unknownClass.shouldBeNull()
            registry.isRegistered("UnknownStepType") shouldBe false
        }
    }
})
