package io.vanillabp.operaton.springboot;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.operaton.bpm.client.ExternalTaskClient;
import org.operaton.bpm.client.spring.boot.starter.ClientProperties;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import io.vanillabp.integration.processservice.SpringBootMigrationAdapterAutoConfiguration;
import io.vanillabp.integration.test.utils.SuppressOutputExtension;
import io.vanillabp.integration.workflowmodule.WorkflowModuleAutoConfiguration;
import io.vanillabp.operaton.springboot.processservice.OperatonProcessServiceConfiguration;

/**
 * A key of Operaton's own Spring Boot starter, <code>operaton.bpm.*</code>, ends the start. Only
 * <code>operaton.bpm.client.*</code> stays, as long as Operaton's starter of the external task
 * client is on the classpath, because that starter reads these keys. An application may use it
 * next to the adapter, to work on the external tasks of another engine.
 * <p>
 * The adapter does not depend on that starter. It finds it by a class name it builds from the
 * name of the engine's starter: <code>org.operaton.bpm.client.spring.boot.starter.ClientProperties</code>.
 * This module has Operaton's starter as a test dependency, in the version of the engine. If a
 * new Operaton version renames or moves {@link ClientProperties}, this test goes red, because
 * then the keys below end the start of every application which uses the starter.
 * <p>
 * <code>OperatonExternalTaskClientVersionOneKeysTest</code> does the same for Camunda's starter
 * and <code>camunda.bpm.client.*</code>.
 */
@ExtendWith(SuppressOutputExtension.class)
public class OperatonEngineExternalTaskClientKeysTest {

  private static final String WORKER_ID = "operaton-worker-next-to-the-adapter";

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withInitializer(new ConfigDataApplicationContextInitializer())
      .withConfiguration(
          AutoConfigurations.of(
              org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration.class,
              org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration.class,
              org.operaton.bpm.client.spring.boot.starter.impl.ClientAutoConfiguration.class,
              OperatonAdapterConfiguration.class,
              OperatonProcessServiceConfiguration.class,
              WorkflowModuleAutoConfiguration.class,
              SpringBootMigrationAdapterAutoConfiguration.class));

  @Test
  @DisplayName("The adapter finds Operaton's starter of the external task client by its class name")
  public void theStarterIsFoundByItsClassName() {

    Assertions.assertTrue(OperatonVersionOneKeys.engineExternalTaskClientIsPresent(getClass().getClassLoader()));

  }

  @Test
  @DisplayName("A key Operaton's external task client reads does not end the start, and it takes effect")
  public void aKeyTheExternalTaskClientReadsIsKept() {

    this.contextRunner
        .withPropertyValues(
            "spring.config.location=classpath:application.yaml",
            "spring.datasource.url=jdbc:h2:mem:operaton-client-keys;DB_CLOSE_DELAY=-1",
            // no engine answers there, and the client must not ask it
            "operaton.bpm.client.base-url=http://localhost:1/engine-rest",
            "operaton.bpm.client.disable-auto-fetching=true",
            "operaton.bpm.client.worker-id="
                + WORKER_ID)
        .run(context -> {

          Assertions.assertNull(context.getStartupFailure(), "context should start");
          Assertions.assertNotNull(context.getBean(ExternalTaskClient.class));
          Assertions.assertEquals(WORKER_ID, context.getBean(ClientProperties.class).getWorkerId());

        });

  }

  @Test
  @DisplayName("With Operaton's starter, any other key of Operaton's starter still ends the start")
  public void anotherKeyOfOperatonsStarterStillEndsTheStart() {

    this.contextRunner
        .withPropertyValues(
            "spring.config.location=classpath:application.yaml",
            "spring.datasource.url=jdbc:h2:mem:operaton-client-and-other-keys;DB_CLOSE_DELAY=-1",
            "operaton.bpm.client.base-url=http://localhost:1/engine-rest",
            "operaton.bpm.client.disable-auto-fetching=true",
            "operaton.bpm.history-level=audit")
        .run(context -> {

          Assertions.assertNotNull(context.getStartupFailure(), "the start has to end");
          var cause = context.getStartupFailure();
          while (cause.getCause() != null) {
            cause = cause.getCause();
          }
          final var message = cause.getMessage();
          Assertions.assertTrue(message.contains("'operaton.bpm.history-level'"), message);
          Assertions.assertFalse(message.contains("client"), message);

        });

  }

}
