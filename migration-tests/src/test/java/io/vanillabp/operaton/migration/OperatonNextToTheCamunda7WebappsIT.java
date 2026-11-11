package io.vanillabp.operaton.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vanillabp.integration.test.utils.SuppressOutputExtension;
import io.vanillabp.operaton.springboot.engine.OperatonEngineHolder;

/**
 * An application which moves from Camunda 7 to Operaton keeps the web applications of the
 * Camunda 7 adapter, <code>camunda7-adapter-spring-boot-webapps</code>, until the old engine has
 * no work left. That module sets <code>camunda.bpm.enabled</code> to <code>false</code> as its
 * default, so that Camunda's starter builds no engine of its own. This adapter ends the start on
 * a key of Camunda's starter, but only where the application wrote it.
 */
@ExtendWith(SuppressOutputExtension.class)
@SuppressOutputExtension.SuppressBackgroundOutput
public class OperatonNextToTheCamunda7WebappsIT {

  /** The web applications run only in a servlet application, on a port nobody else uses. */
  private static final Map<String, Object> WITH_THE_WEB_APPLICATIONS = Map
      .of(
          "spring.main.web-application-type", "servlet",
          "server.port", "0");

  @BeforeAll
  public static void giveOperatonADatabaseOfItsOwn() {

    System.setProperty(MigrationTestApplication.SECOND_DATABASE, "next-to-the-webapps-operaton");

  }

  @AfterAll
  public static void forgetTheSecondDatabase() {

    System.clearProperty(MigrationTestApplication.SECOND_DATABASE);

  }

  private static Map<String, Object> bothAdapters() {

    final var adapters = new HashMap<String, Object>();
    adapters.putAll(MigrationContexts.adapter("c7", "camunda7"));
    adapters.putAll(MigrationContexts.adapter("op", "operaton"));
    adapters.put("adapters.op.data-source-name", "secondEngine");
    adapters.put("prioritized-adapters[0]", "op");
    adapters.put("prioritized-adapters[1]", "c7");
    return adapters;

  }

  @Test
  @DisplayName("The default of the Camunda 7 web applications does not end the start")
  public void theDefaultOfTheWebappsDoesNotEndTheStart() {

    try (var context = MigrationContexts.boot("next-to-the-webapps", bothAdapters(), WITH_THE_WEB_APPLICATIONS)) {

      // the default is there, so the module did its part
      assertEquals("false", context
          .getEnvironment()
          .getProperty("camunda.bpm.enabled"));
      assertTrue(context
          .getBean("Operaton_Engine_op", OperatonEngineHolder.class)
          .getProcessEngine()
          .getName()
          .startsWith("vanillabp-operaton-"));

    }

  }

  @Test
  @DisplayName("camunda.bpm.enabled written by the application still ends the start")
  public void theKeyWrittenByTheApplicationEndsTheStart() {

    final var application = new HashMap<>(WITH_THE_WEB_APPLICATIONS);
    application.put("camunda.bpm.enabled", "false");

    final var refused = assertThrows(
        RuntimeException.class,
        () -> MigrationContexts.boot("next-to-the-webapps-written", bothAdapters(), application).close());

    var cause = (Throwable) refused;
    while (cause.getCause() != null) {
      cause = cause.getCause();
    }
    final var message = cause.getMessage();
    assertTrue(message.startsWith("The configuration still has keys of Camunda's Spring Boot starter"), () -> message);
    assertTrue(message.contains("'camunda.bpm.enabled': VanillaBP builds an engine for every adapter id of type "
        + "operaton."), () -> message);

  }

}
