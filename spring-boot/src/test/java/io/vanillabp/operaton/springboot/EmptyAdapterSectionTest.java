package io.vanillabp.operaton.springboot;

import java.io.IOException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.operaton.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.bind.BindHandler;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.SimpleCommandLinePropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import io.vanillabp.integration.processservice.SpringBootMigrationAdapterAutoConfiguration;
import io.vanillabp.integration.test.utils.SuppressOutputExtension;
import io.vanillabp.integration.workflowmodule.WorkflowModuleAutoConfiguration;
import io.vanillabp.operaton.engine.OperatonEngineProperties;
import io.vanillabp.operaton.springboot.engine.OperatonEngineHolder;
import io.vanillabp.operaton.springboot.processservice.OperatonProcessServiceConfiguration;

/**
 * An adapter section written as <code>{}</code> in a YAML file keeps the keys which other
 * property sources set below it. Spring Boot reads the empty map as an empty value at the
 * section's own name, and that value used to hide every key of the id. See
 * {@link OperatonEmptySections}.
 */
@ExtendWith(SuppressOutputExtension.class)
public class EmptyAdapterSectionTest {

  private static final String ID = "operaton";

  private static final String SECTIONS = "empty-adapter-section/sections.yaml";

  /** Loads both documents of the YAML file and puts the command line above them. */
  private static StandardEnvironment environment(
      final String... commandLine) throws IOException {

    final var environment = new StandardEnvironment();
    // a later document of a YAML file wins over an earlier one, as in Spring Boot
    for (final var document : new YamlPropertySourceLoader().load(SECTIONS, new ClassPathResource(SECTIONS))) {
      environment
          .getPropertySources()
          .addFirst(document);
    }
    environment
        .getPropertySources()
        .addFirst(new SimpleCommandLinePropertySource(commandLine));
    return environment;

  }

  @Test
  @DisplayName("Spring Boot reads an empty YAML section as an empty value at the section's name")
  public void anEmptySectionIsAnEmptyValue() throws IOException {

    final var firstDocument = (EnumerablePropertySource<?>) new YamlPropertySourceLoader()
        .load(SECTIONS, new ClassPathResource(SECTIONS))
        .getFirst();
    // the cause. If Spring Boot stops doing this, OperatonEmptySections is not needed any more
    Assertions.assertEquals("", String.valueOf(firstDocument.getProperty("vanillabp.adapters.operaton")));

    // and the plain binding which the adapter used to do loses the command line
    final var plain = Binder
        .get(environment("--vanillabp.adapters.operaton.tenant-id=from-the-command-line"))
        .bind("vanillabp.adapters.%s".formatted(ID), OperatonEngineProperties.class)
        .orElseGet(OperatonEngineProperties::new);
    Assertions.assertNull(plain.getTenantId());

  }

  @Test
  @DisplayName("The engine keys of an empty section come from the command line and a second YAML document")
  public void theEngineKeysAreKept() throws IOException {

    final var engineProperties = Binder
        .get(environment("--vanillabp.adapters.operaton.tenant-id=from-the-command-line"))
        .bind("vanillabp.adapters.%s".formatted(ID), OperatonEmptySections.section(OperatonEngineProperties.class))
        .orElseGet(OperatonEngineProperties::new);

    Assertions.assertEquals("from-the-command-line", engineProperties.getTenantId());
    Assertions.assertEquals("P2D", engineProperties.getHistoryTimeToLive());

  }

  @Test
  @DisplayName("The overlay keeps the keys of an empty adapter section and of an empty module section")
  public void theOverlayKeepsTheKeys() throws IOException {

    final var environment = environment(
        "--vanillabp.adapters.operaton.allow-listeners=true",
        "--vanillabp.workflow-modules.loans.adapters.operaton.tenant-id=loans-tenant");
    final var overlay = Binder
        .get(environment)
        .bind(
            "vanillabp",
            Bindable.of(VanillaBpOperatonProperties.class),
            OperatonEmptySections
                .advisor()
                .apply(BindHandler.DEFAULT))
        .orElseGet(VanillaBpOperatonProperties::new);

    Assertions.assertTrue(
        overlay
            .enginePropertiesFor(ID)
            .isAllowListeners());
    Assertions.assertEquals(
        "P2D",
        overlay
            .enginePropertiesFor(ID)
            .getHistoryTimeToLive());
    Assertions.assertEquals(
        "loans-tenant",
        overlay
            .configuredTenantFor(ID, "loans")
            .tenantId());

  }

  @Test
  @DisplayName("A booted application runs the engine with the keys set beside an empty section")
  public void theEngineRunsWithTheKeys() {

    new ApplicationContextRunner()
        .withPropertyValues(
            "spring.config.location=classpath:empty-adapter-section/application.yaml",
            "spring.datasource.url=jdbc:h2:mem:operaton-empty-section;DB_CLOSE_DELAY=-1",
            "vanillabp.adapters.operaton.history-time-to-live=P1D",
            "vanillabp.adapters.operaton.db-metrics-reporting=true")
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withConfiguration(
            AutoConfigurations.of(
                org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration.class,
                org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration.class,
                OperatonAdapterConfiguration.class,
                OperatonProcessServiceConfiguration.class,
                WorkflowModuleAutoConfiguration.class,
                SpringBootMigrationAdapterAutoConfiguration.class))
        .run(context -> {

          Assertions.assertNull(context.getStartupFailure(), "context should start");
          final var configuration = (ProcessEngineConfigurationImpl) context
              .getBean(OperatonEngineHolder.class)
              .getProcessEngine()
              .getProcessEngineConfiguration();
          Assertions.assertEquals("P1D", configuration.getHistoryTimeToLive());
          Assertions.assertEquals(
              Boolean.TRUE,
              context
                  .getBean(VanillaBpOperatonProperties.class)
                  .enginePropertiesFor(ID)
                  .getDbMetricsReporting());

        });

  }

}
