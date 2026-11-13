package io.vanillabp.operaton.springboot.it;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.operaton.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.operaton.bpm.engine.impl.db.DbIdGenerator;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import io.vanillabp.integration.processservice.SpringBootMigrationAdapterAutoConfiguration;
import io.vanillabp.integration.test.utils.SuppressOutputExtension;
import io.vanillabp.integration.workflowmodule.WorkflowModuleAutoConfiguration;
import io.vanillabp.operaton.springboot.OperatonAdapterConfiguration;
import io.vanillabp.operaton.springboot.engine.OperatonEngineHolder;
import io.vanillabp.operaton.springboot.processservice.OperatonProcessServiceConfiguration;

/**
 * The key <code>vanillabp.adapters.&lt;id&gt;.id-generator</code> reaches the engine on a
 * booted Spring Boot application, and a value the adapter does not know ends the start. What
 * the two values do to a real engine is {@code OperatonIdGeneratorTest} in the core.
 */
@ExtendWith(SuppressOutputExtension.class)
public class OperatonIdGeneratorKeyTest {

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
  @DisplayName("'db' gives the engine its DbIdGenerator, and a deployment gets a number")
  public void dbReachesTheEngine() {

    this.contextRunner
        .withPropertyValues(
            "spring.config.location=classpath:application.yaml",
            "spring.datasource.url=jdbc:h2:mem:c7-id-generator-db;DB_CLOSE_DELAY=-1",
            "vanillabp.adapters.c7.id-generator=db")
        .run(context -> {

          Assertions.assertNull(context.getStartupFailure(), "context should start");
          final var engine = context
              .getBean(OperatonEngineHolder.class)
              .getProcessEngine();
          Assertions.assertInstanceOf(DbIdGenerator.class, ((ProcessEngineConfigurationImpl) engine
              .getProcessEngineConfiguration()).getIdGenerator());
          final var deployment = engine
              .getRepositoryService()
              .createDeployment()
              .addString("empty.txt", "")
              .name("id-generator-key-test")
              .deploy();
          Assertions.assertTrue(deployment.getId().matches("[0-9]+"), deployment::getId);

        });

  }

  @Test
  @DisplayName("an unknown value ends the start and names the allowed values")
  public void anUnknownValueEndsTheStart() {

    this.contextRunner
        .withPropertyValues(
            "spring.config.location=classpath:application.yaml",
            "spring.datasource.url=jdbc:h2:mem:c7-id-generator-unknown;DB_CLOSE_DELAY=-1",
            "vanillabp.adapters.c7.id-generator=strong")
        .run(context -> {

          var cause = context.getStartupFailure();
          Assertions.assertNotNull(cause, "the start should end");
          while (cause.getCause() != null) {
            cause = cause.getCause();
          }
          final var message = cause.getMessage();
          Assertions.assertTrue(message.contains("'vanillabp.adapters.c7.id-generator' is 'strong'"), message);
          Assertions.assertTrue(message.contains("Use 'uuid' or 'db'."), message);

        });

  }

}
