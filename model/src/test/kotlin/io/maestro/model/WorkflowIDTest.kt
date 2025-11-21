package io.maestro.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.throwable.shouldHaveMessage
import io.maestro.model.exception.MalformedWorkflowIDException

class WorkflowIDTest : FeatureSpec({

    feature("toString method") {
        scenario("should format WorkflowID as namespace:id") {
            val workflowId = WorkflowID("production", "payment-workflow")
            workflowId.toString() shouldBe "production:payment-workflow"
        }

        scenario("should handle namespaces with hyphens and underscores") {
            val workflowId = WorkflowID("prod-staging_test", "workflow-1")
            workflowId.toString() shouldBe "prod-staging_test:workflow-1"
        }

        scenario("should handle IDs with hyphens and underscores") {
            val workflowId = WorkflowID("production", "payment_processing-v2")
            workflowId.toString() shouldBe "production:payment_processing-v2"
        }

        scenario("should handle alphanumeric namespace and id") {
            val workflowId = WorkflowID("ns123", "id456")
            workflowId.toString() shouldBe "ns123:id456"
        }

        scenario("should produce consistent output for same inputs") {
            val workflowId1 = WorkflowID("production", "workflow-1")
            val workflowId2 = WorkflowID("production", "workflow-1")
            workflowId1.toString() shouldBe workflowId2.toString()
        }
    }

    feature("parse method") {
        scenario("should parse valid namespace:id format") {
            val parsed = WorkflowID.parse("production:payment-workflow")
            parsed.namespace shouldBe "production"
            parsed.id shouldBe "payment-workflow"
        }

        scenario("should parse namespace with hyphens and underscores") {
            val parsed = WorkflowID.parse("prod-staging_test:workflow-1")
            parsed.namespace shouldBe "prod-staging_test"
            parsed.id shouldBe "workflow-1"
        }

        scenario("should parse ID with hyphens and underscores") {
            val parsed = WorkflowID.parse("production:payment_processing-v2")
            parsed.namespace shouldBe "production"
            parsed.id shouldBe "payment_processing-v2"
        }

        scenario("should parse alphanumeric namespace and id") {
            val parsed = WorkflowID.parse("ns123:id456")
            parsed.namespace shouldBe "ns123"
            parsed.id shouldBe "id456"
        }

        scenario("should throw exception for invalid format without colon") {
            val exception = shouldThrow<MalformedWorkflowIDException> {
                WorkflowID.parse("production-payment-workflow")
            }
            exception shouldHaveMessage "Invalid WorkflowID format: production-payment-workflow (expected namespace:id)"
            exception.input shouldBe "production-payment-workflow"
        }

        scenario("should throw exception for invalid format with multiple colons") {
            val exception = shouldThrow<MalformedWorkflowIDException> {
                WorkflowID.parse("production:payment:workflow")
            }
            exception shouldHaveMessage "Invalid WorkflowID format: production:payment:workflow (expected namespace:id)"
            exception.input shouldBe "production:payment:workflow"
        }

        scenario("should throw exception for empty string") {
            val exception = shouldThrow<MalformedWorkflowIDException> {
                WorkflowID.parse("")
            }
            exception.message shouldNotBe null
            exception.input shouldBe ""
        }

        scenario("should throw exception when namespace is empty after parsing") {
            val exception = shouldThrow<MalformedWorkflowIDException> {
                WorkflowID.parse(":payment-workflow")
            }
            exception shouldHaveMessage "Namespace must not be blank in WorkflowID: :payment-workflow"
            exception.input shouldBe ":payment-workflow"
        }

        scenario("should throw exception when id is empty after parsing") {
            val exception = shouldThrow<MalformedWorkflowIDException> {
                WorkflowID.parse("production:")
            }
            exception shouldHaveMessage "ID must not be blank in WorkflowID: production:"
            exception.input shouldBe "production:"
        }

        scenario("should be round-trip compatible with toString") {
            val original = WorkflowID("production", "payment-workflow")
            val parsed = WorkflowID.parse(original.toString())
            parsed.namespace shouldBe original.namespace
            parsed.id shouldBe original.id
        }

        scenario("should be round-trip compatible with toString for complex names") {
            val original = WorkflowID("prod-staging_test", "payment_processing-v2")
            val parsed = WorkflowID.parse(original.toString())
            parsed.namespace shouldBe original.namespace
            parsed.id shouldBe original.id
        }
    }
})
