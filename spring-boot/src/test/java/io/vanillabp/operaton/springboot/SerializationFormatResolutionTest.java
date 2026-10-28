package io.vanillabp.operaton.springboot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vanillabp.integration.test.utils.SuppressOutputExtension;
import io.vanillabp.operaton.engine.OperatonEngineProperties;


/**
 * The serialization format of nested shared values is resolved per workflow,
 * with a fallback to the workflow module and to the adapter.
 */
@ExtendWith(SuppressOutputExtension.class)
public class SerializationFormatResolutionTest {

  private static final String ADAPTER = "c7";

  private VanillaBpOperatonProperties properties(
      final String adapterFormat,
      final String moduleFormat,
      final String workflowFormat) {

    final var properties = new VanillaBpOperatonProperties();
    final var engineProperties = new OperatonEngineProperties();
    engineProperties.setSerializationFormat(adapterFormat);
    properties.setAdapters(Map.of(ADAPTER, engineProperties));

    final var module = new VanillaBpOperatonProperties.OperatonWorkflowModuleProperties();
    if (moduleFormat != null) {
      final var scoped = new VanillaBpOperatonProperties.OperatonModuleScopedProperties();
      scoped.setSerializationFormat(moduleFormat);
      module.setAdapters(Map.of(ADAPTER, scoped));
    }
    if (workflowFormat != null) {
      final var workflow = new VanillaBpOperatonProperties.OperatonWorkflowProperties();
      final var scoped = new VanillaBpOperatonProperties.OperatonScopedProperties();
      scoped.setSerializationFormat(workflowFormat);
      workflow.setAdapters(Map.of(ADAPTER, scoped));
      module.setWorkflows(Map.of("TheWorkflow", workflow));
    }
    properties.setWorkflowModules(Map.of("the-module", module));
    return properties;

  }

  @Test
  @DisplayName("The workflow wins over its module, the module over the adapter")
  public void mostSpecificWins() {

    assertEquals(
        "application/workflow",
        properties("application/adapter", "application/module", "application/workflow")
            .serializationFormatFor(ADAPTER, "the-module", "TheWorkflow"));

    assertEquals(
        "application/module",
        properties("application/adapter", "application/module", null)
            .serializationFormatFor(ADAPTER, "the-module", "TheWorkflow"));

    assertEquals(
        "application/adapter",
        properties("application/adapter", null, null)
            .serializationFormatFor(ADAPTER, "the-module", "TheWorkflow"));

    // another workflow of the same module falls back to the module's value
    assertEquals(
        "application/module",
        properties("application/adapter", "application/module", "application/workflow")
            .serializationFormatFor(ADAPTER, "the-module", "AnotherWorkflow"));

  }

  @Test
  @DisplayName("Nothing configured is null - the engine's own default then applies")
  public void nothingConfiguredIsNull() {

    assertNull(properties(null, null, null).serializationFormatFor(ADAPTER, "the-module", "TheWorkflow"));
    assertNull(new VanillaBpOperatonProperties().serializationFormatFor(ADAPTER, "unknown", "Unknown"));
    // a blank value counts as unset
    assertNull(properties("  ", null, null).serializationFormatFor(ADAPTER, "the-module", "TheWorkflow"));

  }

}
