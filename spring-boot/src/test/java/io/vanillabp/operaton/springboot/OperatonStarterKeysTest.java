package io.vanillabp.operaton.springboot;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import io.vanillabp.integration.processservice.SpringBootMigrationAdapterAutoConfiguration;
import io.vanillabp.integration.test.utils.SuppressOutputExtension;
import io.vanillabp.integration.workflowmodule.WorkflowModuleAutoConfiguration;
import io.vanillabp.operaton.springboot.processservice.OperatonProcessServiceConfiguration;

/**
 * A key of Operaton's own Spring Boot starter, <code>operaton.bpm.*</code>, ends the start like
 * a key of Camunda's starter. The adapter builds the engine itself, so no starter reads these
 * keys, and each of them would change nothing. The check comes from the Camunda 7 adapter. The
 * name <code>operaton</code> in it comes from the rule in <code>rewrite/vanillabp-operaton.yml</code>
 * which sets <code>ENGINE_STARTER_NAME</code>. Without that rule this test fails.
 * <p>
 * The webapps module is not on this module's classpath. So here even
 * <code>operaton.bpm.webapp.*</code> ends the start. <code>OperatonWebappsVersionOneKeysTest</code>
 * in the webapps module shows that it does not there.
 */
@ExtendWith(SuppressOutputExtension.class)
public class OperatonStarterKeysTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withInitializer(new ConfigDataApplicationContextInitializer())
      .withConfiguration(
          AutoConfigurations.of(
              org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration.class,
              org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration.class,
              OperatonAdapterConfiguration.class,
              OperatonProcessServiceConfiguration.class,
              WorkflowModuleAutoConfiguration.class,
              SpringBootMigrationAdapterAutoConfiguration.class));

  @Test
  @DisplayName("A key of Operaton's starter ends the start and names the key of VanillaBP")
  public void aKeyOfOperatonsStarterEndsTheStart() {

    this.contextRunner
        .withPropertyValues(
            "spring.config.location=classpath:application.yaml",
            "spring.datasource.url=jdbc:h2:mem:operaton-starter-keys;DB_CLOSE_DELAY=-1",
            "operaton.bpm.history-level=audit",
            "operaton.bpm.webapp.application-path=/engine-ui",
            "OPERATON_BPM_JOB_EXECUTION_MAX_WAIT=5000")
        .run(context -> {

          Assertions.assertNotNull(context.getStartupFailure(), "the start has to end");
          var cause = context.getStartupFailure();
          while (cause.getCause() != null) {
            cause = cause.getCause();
          }
          final var message = cause.getMessage();
          Assertions.assertTrue(message.contains("The keys below 'operaton.bpm.*' belong to the Spring Boot starter "
              + "of the Operaton engine. VanillaBP 2 does not use that starter either."), message);
          Assertions.assertTrue(
              message
                  .contains("  - 'operaton.bpm.history-level': Write 'vanillabp.adapters.c7.history-level' instead."),
              message);
          Assertions.assertTrue(message.contains("  - 'operaton.bpm.webapp.application-path': Add the module "
              + "operaton-adapter-spring-boot-webapps"), message);
          Assertions.assertTrue(message.contains("  - 'OPERATON_BPM_JOB_EXECUTION_MAX_WAIT': Version 2 has no key"),
              message);

        });

  }

}
