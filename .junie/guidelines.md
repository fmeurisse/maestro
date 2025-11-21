Project-specific development guidelines

This document captures details that are specific to this repository to help advanced contributors work efficiently.

Build and configuration
- Toolchain
  - JDK: 21 (configured via parent POM property `java.version`)
  - Kotlin: 2.2.0 (parent POM property `kotlin.version`)
  - Maven: 3.9.x recommended

- Modules (Maven multi-module)
  - `model`: Pure Kotlin domain model and exceptions. Contains tests using Kotest/JUnit 5.
  - `core`: Core services and YAML parsing. Uses Jackson (YAML + Kotlin modules) and Jakarta CDI. Kotlin `all-open` compiler plugin is enabled for CDI annotations.
  - `api`: Quarkus application layer (Quarkus 3.29.3), depends on `core` and `model`.
  - `plugins/postgres`: Postgres plugin module (details omitted here).
  - `ui`: Frontend resources.

- Common build commands
  - Build all modules without tests:
    - `mvn -q -DskipTests package`
  - Build and test everything:
    - `mvn -q verify`
  - Build or test a specific module (plus its required dependencies):
    - `mvn -q -pl model -am package`
    - `mvn -q -pl core -am test`
  - Speed tips:
    - Use `-q` for quieter logs, `-T 1C` for parallel module builds on multi-core: `mvn -T 1C verify`.

- IDE notes (IntelliJ IDEA)
  - Import as Maven project; Kotlin sources are under `src/main/kotlin` and `src/test/kotlin` for modules that have tests.
  - Language level must be 21 to match the build. Ensure Maven toolchain or Project SDK is set accordingly.
  - For `core`, the Kotlin `all-open` plugin makes CDI-scoped classes non-final at compile-time. Annotations recognized:
    - `jakarta.enterprise.context.ApplicationScoped`
    - `jakarta.inject.Singleton`

Running and writing tests
- Frameworks in use
  - JUnit Jupiter `5.10.2`, Kotest `5.9.x` used as the test DSL/runner in `model`. Surefire version is `${surefire-plugin.version}` = `3.2.5` (from parent POM).

- Directory layout
  - Tests go in `src/test/kotlin` under the respective module. For example, `model/src/test/kotlin/io/maestro/model/WorkflowIDTest.kt`.

- How to run tests (verified)
  - Run tests for a single module (verified):
    - `mvn -q -pl model test`
    - Example single-test execution (verified):
      - `mvn -q -pl model -Dtest=WorkflowIDTest test`
  - Run all modules:
    - `mvn -q test`
  - Run a single JUnit method (if using a plain JUnit 5 test class):
    - `mvn -q -pl model -Dtest=MyClassTest#myMethod test`

- Kotest notes
  - You will see a startup warning about autoscan:
    - `Kotest autoscan is enabled ... set 'kotest.framework.classpath.scanning.autoscan.disable=true' to disable`
  - To silence and slightly speed up startup, either:
    - Add to Maven command: `-Dkotest.framework.classpath.scanning.autoscan.disable=true`
    - Or configure Surefire `argLine` in the module POM to include that system property.

- Authoring new tests
  - Kotest example (FeatureSpec):
    ```kotlin
    class MyFeatureSpecTest : FeatureSpec({
      feature("WorkflowID formatting") {
        scenario("namespace:id") {
          WorkflowID("production", "payments").toString() shouldBe "production:payments"
        }
      }
    })
    ```
  - Plain JUnit 5 example:
    ```kotlin
    class MyJUnitTest {
      @Test
      fun `toString formats correctly`() {
        assertEquals("ns:id", WorkflowID("ns", "id").toString())
      }
    }
    ```
  - Naming: Surefire picks up any standard class names ending with `Test`/`Spec` when using JUnit 5/Kotest. Place tests under matching package structure for clarity.

- Demonstrated commands (checked before documenting)
  - Single known test run in `model`:
    - `mvn -q -pl model -Dtest=WorkflowIDTest test` → executed successfully (Kotest startup warning may appear; safe to ignore or disable via property).

Quarkus/API module notes
- Local dev mode for the API:
  - `mvn -q -pl api quarkus:dev`
  - Quarkus platform version: `3.29.3` (managed in parent POM). If you alter Quarkus extensions, keep versions consistent with the BOM in the parent POM.

Code organization and style
- Kotlin style: follow idiomatic Kotlin as per module conventions already present.
- Module boundaries:
  - `model` should remain free of framework-specific concerns; only domain objects, value types, and domain exceptions live here.
  - `core` contains parsing and service logic; leverages Jackson for YAML (`jackson-dataformat-yaml` and `jackson-module-kotlin`). CDI annotations are supported via Kotlin `all-open`.
  - `api` encapsulates Quarkus runtime specifics and should not leak into `model`.

Version and dependency management
- Centralized in the parent POM:
  - `${java.version}`, `${kotlin.version}`, `${quarkus.version}`, `${quarkus-plugin.version}`, `${surefire-plugin.version}`.
  - Quarkus dependencies are managed through the platform BOM: `io.quarkus.platform:quarkus-bom`.

Troubleshooting
- “No tests found” when running a single test:
  - Ensure you target the correct module with `-pl <module>` and that the class is under `src/test/kotlin`.
  - For Kotest, ensure your test class extends a Kotest spec (e.g., `FeatureSpec`) or is a valid JUnit 5 test.
- Kotest autoscan overhead:
  - Disable via `-Dkotest.framework.classpath.scanning.autoscan.disable=true` if startup scanning is undesirable during local dev.
