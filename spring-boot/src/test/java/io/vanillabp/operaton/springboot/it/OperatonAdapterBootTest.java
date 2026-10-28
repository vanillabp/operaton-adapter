package io.vanillabp.operaton.springboot.it;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.operaton.bpm.engine.ProcessEngine;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import io.vanillabp.integration.adapter.spi.AdapterDeploymentService;
import io.vanillabp.integration.processservice.SpringBootMigrationAdapterAutoConfiguration;
import io.vanillabp.integration.test.utils.SuppressOutputExtension;
import io.vanillabp.integration.workflowmodule.WorkflowModuleAutoConfiguration;
import io.vanillabp.operaton.deployment.OperatonDeploymentService;
import io.vanillabp.operaton.engine.OperatonInstanceIdentity;
import io.vanillabp.operaton.springboot.OperatonAdapterConfiguration;
import io.vanillabp.operaton.springboot.engine.OperatonEngineHolder;
import io.vanillabp.operaton.springboot.processservice.OperatonProcessServiceConfiguration;

/**
 * Boot smoke test. It proves that the Operaton adapter is found through the
 * configuration alone ({@code vanillabp.adapters.c7.type: operaton}). The Operaton beans
 * resolve the embedded engine's DataSource lazily. So the test wires a plain-JDBC H2 setup
 * ({@code DataSourceAutoConfiguration} and
 * {@code DataSourceTransactionManagerAutoConfiguration}), with NO JPA on purpose. There
 * are no BPMN files and no {@code @WorkflowService} classes, so no workflow is deployed.
 * <p>
 * The deployment lifecycle ({@code DeploymentAutoConfiguration}) is NOT loaded, except in
 * {@code twoAdapterIdsSharingTheSameDataSourceFailWithGuidingMessage}. That test needs it,
 * because the deployment pipeline is what checks two adapter ids for being distinct at
 * startup. Deploying real BPMN is covered by the integration-tests module. These tests prove
 * discovery, registration and the startup checks only.
 */
@ExtendWith(SuppressOutputExtension.class)
public class OperatonAdapterBootTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

  @Test
  public void adapterIsDiscoveredByConfiguration() {

    this.contextRunner
        .withPropertyValues(
            "spring.config.location=classpath:application.yaml",
            "spring.datasource.url=jdbc:h2:mem:operaton-boot-test;DB_CLOSE_DELAY=-1")
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

          // the embedded engine was wired (plain JDBC, no JPA) and named after the
          // adapter id. Its job executor is still INACTIVE, because
          // startWorkflowProcessing starts it (deferred activation)
          final var engine = context.getBean(ProcessEngine.class);
          Assertions.assertEquals("vanillabp-operaton-c7", engine.getName());
          final var holder = context.getBean(OperatonEngineHolder.class);
          Assertions.assertFalse(
              holder.isJobExecutorActive(),
              "the job executor must not be active before startWorkflowProcessing");
          Assertions.assertFalse(holder.usesSeparateDataSource());

          // element-bean convention: one AdapterDeploymentService bean per adapter,
          // never a List bean. So several adapter types can live side by side
          final var deploymentService = context.getBean(AdapterDeploymentService.class);
          Assertions.assertEquals("c7", deploymentService.getAdapterId());
          Assertions.assertEquals(
              OperatonDeploymentService.ADAPTER_TYPE,
              deploymentService.getAdapterType());
          Assertions.assertEquals("operaton", deploymentService.getAdapterType());

        });

  }

  @Test
  public void twoAdapterIdsOfTypeOperatonYieldPerIdEnginesAndBeans() {

    // per-adapter-id convention: TWO ids of type operaton get one ENGINE, one process
    // service and one deployment service PER id. The second id needs its own datasource,
    // because two embedded engines must never share one schema (see the validation test
    // below). It names that datasource BY BEAN NAME. Setting up datasources is the
    // application's job, VanillaBP never builds a pool
    this.contextRunner
        .withBean(
            "c7TwoDataSource",
            javax.sql.DataSource.class,
            () -> new org.springframework.jdbc.datasource.SimpleDriverDataSource(
                new org.h2.Driver(), "jdbc:h2:mem:operaton-two-ids-own;DB_CLOSE_DELAY=-1"),
            // keep the bean out of by-type injection, so Spring Boot's
            // default-datasource auto-configuration stays active. This is the usual
            // pattern for additional application datasources
            beanDefinition -> ((org.springframework.beans.factory.support.AbstractBeanDefinition) beanDefinition)
                .setDefaultCandidate(false))
        .withPropertyValues(
            "spring.config.location=classpath:application.yaml",
            "spring.datasource.url=jdbc:h2:mem:operaton-two-ids-test;DB_CLOSE_DELAY=-1",
            "vanillabp.prioritized-adapters=c7,c7-two",
            "vanillabp.adapters.c7-two.type=operaton",
            "vanillabp.adapters.c7-two.data-source-name=c7TwoDataSource",
            "vanillabp.workflow-modules.c7-smoke-test.adapters.c7-two.resources-location=classpath*:c7-smoke-test/processes-two")
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

          // one engine per adapter id, named after the id
          final var engineNames = context
              .getBeansOfType(ProcessEngine.class)
              .values()
              .stream()
              .map(ProcessEngine::getName)
              .collect(java.util.stream.Collectors.toSet());
          Assertions.assertEquals(
              java.util.Set.of("vanillabp-operaton-c7", "vanillabp-operaton-c7-two"),
              engineNames);

          // the id with its own datasource is marked as such. Its starts use the
          // two-phase pattern (see OperatonProcessService)
          final var holders = context.getBeansOfType(OperatonEngineHolder.class);
          Assertions.assertFalse(holders.get("Operaton_Engine_c7").usesSeparateDataSource());
          Assertions.assertTrue(holders.get("Operaton_Engine_c7-two").usesSeparateDataSource());

          final var deploymentServiceIds = context
              .getBeanProvider(AdapterDeploymentService.class)
              .stream()
              .map(service -> ((AdapterDeploymentService<?, ?>) service).getAdapterId())
              .collect(java.util.stream.Collectors.toSet());
          Assertions.assertEquals(java.util.Set.of("c7", "c7-two"), deploymentServiceIds);

          // what the platform compares across adapter types is built from datasource and
          // prefix, exactly as two operaton ids are compared
          final var tablesByAdapterId = context
              .getBeanProvider(AdapterDeploymentService.class)
              .stream()
              .map(service -> (AdapterDeploymentService<?, ?>) service)
              .collect(java.util.stream.Collectors
                  .toMap(AdapterDeploymentService::getAdapterId, AdapterDeploymentService::getAddressedSystem));
          Assertions.assertEquals(
              java.util.Optional.of(new OperatonInstanceIdentity(null, null).addressedSystem()),
              tablesByAdapterId.get("c7"));
          Assertions.assertEquals(
              java.util.Optional.of(new OperatonInstanceIdentity("c7TwoDataSource", null).addressedSystem()),
              tablesByAdapterId.get("c7-two"));

          final var processServices = context
              .getBeanProvider(io.vanillabp.integration.adapter.spi.MigratableProcessService.class)
              .stream()
              .map(service -> (io.vanillabp.integration.adapter.spi.MigratableProcessService<?>) service)
              .collect(java.util.stream.Collectors
                  .toMap(io.vanillabp.integration.adapter.spi.MigratableProcessService::getAdapterId,
                      service -> service));
          Assertions.assertEquals(java.util.Set.of("c7", "c7-two"), processServices.keySet());

          // an embedded engine answers the election by asking its own tables. So this
          // adapter never guesses, and it may be combined with a second BPMS
          processServices
              .values()
              .forEach(service -> Assertions.assertTrue(
                  service.canLocateWorkflows(),
                  "Operaton can be asked which workflows it holds"));

        });

  }

  @Test
  public void engineFactsAreRegisteredPerAdapterIdAndAnswerTheAdaptersOwnAnswer() {

    // what an extension of this adapter asks about the engine it runs in. There is one
    // OperatonEngineFacts bean per configured adapter id. The id with a data source of
    // its own is reported as NOT joining the application's transaction
    this.contextRunner
        .withBean(
            "c7FactsDataSource",
            javax.sql.DataSource.class,
            () -> new org.springframework.jdbc.datasource.SimpleDriverDataSource(
                new org.h2.Driver(), "jdbc:h2:mem:operaton-facts-own;DB_CLOSE_DELAY=-1"),
            beanDefinition -> ((org.springframework.beans.factory.support.AbstractBeanDefinition) beanDefinition)
                .setDefaultCandidate(false))
        .withPropertyValues(
            "spring.config.location=classpath:application.yaml",
            "spring.datasource.url=jdbc:h2:mem:operaton-facts-test;DB_CLOSE_DELAY=-1",
            "vanillabp.prioritized-adapters=c7,c7-own",
            "vanillabp.adapters.c7-own.type=operaton",
            "vanillabp.adapters.c7-own.data-source-name=c7FactsDataSource",
            "vanillabp.workflow-modules.c7-smoke-test.adapters.c7-own.resources-location=classpath*:c7-smoke-test/processes-two")
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

          final var facts = context.getBeansOfType(io.vanillabp.operaton.api.OperatonEngineFacts.class);
          Assertions.assertEquals(
              java.util.Set.of("Operaton_EngineFacts_c7", "Operaton_EngineFacts_c7-own"),
              facts.keySet(),
              "one OperatonEngineFacts bean per configured adapter id");

          final var shared = facts.get("Operaton_EngineFacts_c7");
          Assertions.assertEquals("c7", shared.adapterId());
          Assertions.assertTrue(
              shared.joinsTheApplicationTransaction(),
              "an engine on the application's data source commits with it");
          Assertions.assertEquals(
              "c7-smoke-test",
              shared.tenantIdOf("c7-smoke-test"),
              "without a configured name the tenant is the workflow module");
          Assertions.assertSame(
              context.getBean("Operaton_Engine_c7", OperatonEngineHolder.class).getTaskRegistry(),
              shared.taskRegistry(),
              "the registry handed out is the engine's own");

          final var own = facts.get("Operaton_EngineFacts_c7-own");
          Assertions.assertEquals("c7-own", own.adapterId());
          Assertions.assertFalse(
              own.joinsTheApplicationTransaction(),
              "an engine on a data source of its own writes to a resource the application does not join");

        });

  }

  @Test
  public void twoAdapterIdsSharingTheSameDataSourceFailWithGuidingMessage() {

    // two embedded engines on one schema share the same engine state. Configuring
    // them as two adapters must fail at startup with a guiding message
    this.contextRunner
        .withPropertyValues(
            "spring.config.location=classpath:application.yaml",
            "spring.datasource.url=jdbc:h2:mem:operaton-same-ds-test;DB_CLOSE_DELAY=-1",
            "vanillabp.prioritized-adapters=c7,c7-two",
            "vanillabp.adapters.c7-two.type=operaton",
            "vanillabp.workflow-modules.c7-smoke-test.adapters.c7-two.resources-location=classpath*:c7-smoke-test/processes-two")
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withConfiguration(
            AutoConfigurations.of(
                org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration.class,
                org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration.class,
                OperatonAdapterConfiguration.class,
                OperatonProcessServiceConfiguration.class,
                WorkflowModuleAutoConfiguration.class,
                SpringBootMigrationAdapterAutoConfiguration.class,
                // whether two ids of one type are distinct is checked through the
                // adapter SPI hook. The deployment pipeline calls it at startup
                io.vanillabp.integration.deployment.DeploymentAutoConfiguration.class))
        .run(context -> {

          Assertions.assertNotNull(context.getStartupFailure(), "boot has to fail with a guiding message");

          var cause = context.getStartupFailure();
          while (cause.getCause() != null) {
            cause = cause.getCause();
          }
          final var message = String.valueOf(cause.getMessage());
          Assertions.assertTrue(
              message.contains("'c7', 'c7-two'"),
              "expected the guiding message naming both adapter ids but got: "
                  + message);
          Assertions.assertTrue(
              message.contains("run on the SAME engine database"),
              "expected the guiding message but got: "
                  + message);
          Assertions.assertTrue(message.contains("data-source-name"));
          Assertions.assertTrue(
              message.contains("table-prefix"),
              "the message has to name the second way of making two ids distinct");

        });

  }

  /**
   * A fork of Camunda 7 uses the same tables. A <code>operaton</code> id and an id of a fork
   * adapter on the same datasource without prefixes would be two engines on one set of
   * tables, and each job executor would run the jobs of the other one. The check of one
   * adapter type cannot see that, so the platform compares the tables across types.
   */
  @Test
  public void aOperatonIdAndAForkIdOnTheSameTablesFailWithGuidingMessage() {

    this.contextRunner
        .withBean(
            "cibsevenDouble",
            ForkAdapterDouble.class,
            () -> new ForkAdapterDouble(
                "cib", "cibseven", new OperatonInstanceIdentity(null, null).addressedSystem()))
        .withPropertyValues(
            "spring.config.location=classpath:application.yaml",
            "spring.datasource.url=jdbc:h2:mem:operaton-fork-same-tables-test;DB_CLOSE_DELAY=-1")
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withConfiguration(
            AutoConfigurations.of(
                org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration.class,
                org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration.class,
                OperatonAdapterConfiguration.class,
                OperatonProcessServiceConfiguration.class,
                WorkflowModuleAutoConfiguration.class,
                SpringBootMigrationAdapterAutoConfiguration.class,
                io.vanillabp.integration.deployment.DeploymentAutoConfiguration.class))
        .run(context -> {

          final var message = rootCauseMessage(context.getStartupFailure());
          Assertions.assertTrue(
              message.contains("The adapters 'c7' (type 'operaton'), 'cib' (type 'cibseven') work on the SAME "
                  + "system: datasource <the application's default datasource>, table prefix <no table prefix>."),
              () -> message);
          Assertions.assertTrue(message.contains("for type 'operaton', 'cibseven':"), () -> message);
          Assertions.assertTrue(message.contains("'vanillabp.adapters.<id>.data-source-name'"), () -> message);
          Assertions.assertTrue(message.contains("'vanillabp.adapters.<id>.table-prefix'"), () -> message);

        });

  }

  /**
   * The migration case: a fork adapter on tables of its own, next to <code>operaton</code>.
   */
  @Test
  public void aOperatonIdAndAForkIdOnTablesOfTheirOwnStart() {

    this.contextRunner
        .withBean(
            "cibsevenDouble",
            ForkAdapterDouble.class,
            () -> new ForkAdapterDouble(
                "cib", "cibseven", new OperatonInstanceIdentity(null, "CIB_").addressedSystem()))
        .withPropertyValues(
            "spring.config.location=classpath:application.yaml",
            "spring.datasource.url=jdbc:h2:mem:operaton-fork-own-tables-test;DB_CLOSE_DELAY=-1")
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withConfiguration(
            AutoConfigurations.of(
                org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration.class,
                org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration.class,
                OperatonAdapterConfiguration.class,
                OperatonProcessServiceConfiguration.class,
                WorkflowModuleAutoConfiguration.class,
                SpringBootMigrationAdapterAutoConfiguration.class,
                io.vanillabp.integration.deployment.DeploymentAutoConfiguration.class))
        .run(context -> {

          final var failure = context.getStartupFailure();
          Assertions.assertTrue(
              (failure == null) || !rootCauseMessage(failure).contains("work on the SAME system"),
              () -> rootCauseMessage(failure));
          Assertions.assertNull(failure, "context should start");

        });

  }

  @Test
  public void aTablePrefixWithSchemaUpdateFailsWithGuidingMessage() {

    // Camunda's schema management ignores the prefix and would create a set of
    // unprefixed ACT_* tables in the shared database. The check runs before the engine
    // is built. So the application learns about it, and does not collect stray tables
    // and a MyBatis stack trace
    this.contextRunner
        .withPropertyValues(
            "spring.config.location=classpath:application.yaml",
            "spring.datasource.url=jdbc:h2:mem:operaton-prefix-schema-update-test;DB_CLOSE_DELAY=-1",
            "vanillabp.adapters.c7.table-prefix=NEW_")
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

          final var message = rootCauseMessage(context.getStartupFailure());
          Assertions.assertTrue(message.contains("'NEW_'"), () -> message);
          Assertions.assertTrue(message.contains("'database-schema-update: true'"), () -> message);
          Assertions
              .assertTrue(
                  message.contains("vanillabp.adapters.c7.database-schema-update: false"),
                  () -> message);
          Assertions.assertTrue(message.contains("vanillabp.adapters.c7.data-source-name"), () -> message);

          // and nothing was created while failing, because the check runs before the
          // engine
          try (var connection = java.sql.DriverManager
              .getConnection("jdbc:h2:mem:operaton-prefix-schema-update-test;DB_CLOSE_DELAY=-1")) {
            try (var tables = connection.getMetaData().getTables(null, null, "ACT%", new String[]{
                "TABLE"
            })) {
              Assertions.assertFalse(tables.next(), "no ACT_* table may have been created");
            }
          }

        });

  }

  @Test
  public void missingPrefixedTablesFailWithGuidingMessage() {

    // the prefixed engine is switched to 'the tables exist already', and they do
    // not. The message names them and both ways to go on
    this.contextRunner
        .withPropertyValues(
            "spring.config.location=classpath:application.yaml",
            "spring.datasource.url=jdbc:h2:mem:operaton-prefix-missing-test;DB_CLOSE_DELAY=-1",
            "vanillabp.adapters.c7.table-prefix=NEW_",
            "vanillabp.adapters.c7.database-schema-update=false")
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

          final var message = rootCauseMessage(context.getStartupFailure());
          Assertions.assertTrue(message.contains("NEW_ACT_RU_EXECUTION"), () -> message);
          Assertions.assertTrue(message.contains("the application's default datasource"), () -> message);
          Assertions.assertTrue(message.contains("vanillabp.adapters.c7.data-source-name"), () -> message);

        });

  }

  /**
   * @param startupFailure The failure of a context which had to fail
   * @return The message of its innermost cause
   */
  private static String rootCauseMessage(
      final Throwable startupFailure) {

    Assertions.assertNotNull(startupFailure, "boot has to fail with a guiding message");
    var cause = startupFailure;
    while (cause.getCause() != null) {
      cause = cause.getCause();
    }
    return String.valueOf(cause.getMessage());

  }

  @Test
  public void configuredAdapterWithoutDataSourceFailsWithGuidingMessage() {

    // a configured operaton adapter WITHOUT any DataSource. The boot fails with a
    // guiding message which names the remedy, not with a
    // NoSuchBeanDefinitionException. Configuration defects show up at startup
    this.contextRunner
        .withPropertyValues("spring.config.location=classpath:application.yaml")
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withConfiguration(
            AutoConfigurations.of(
                OperatonAdapterConfiguration.class,
                OperatonProcessServiceConfiguration.class,
                WorkflowModuleAutoConfiguration.class,
                SpringBootMigrationAdapterAutoConfiguration.class))
        .run(context -> {

          Assertions.assertNotNull(context.getStartupFailure(), "boot has to fail with a guiding message");

          var cause = context.getStartupFailure();
          while (cause.getCause() != null) {
            cause = cause.getCause();
          }
          final var message = String.valueOf(cause.getMessage());
          Assertions.assertTrue(
              message.contains("Operaton adapter 'c7' is configured"),
              "expected the guiding message but got: "
                  + message);
          Assertions.assertTrue(message.contains("no DataSource/PlatformTransactionManager is available"));
          Assertions.assertTrue(message.contains("spring.datasource."));

        });

  }

  @Test
  public void withAnotherBpmsConfiguredNoOperatonEngineIsCreated() {

    // the adapter jar is on the classpath, but ANOTHER BPMS is configured. Then the
    // gate must prevent the engine entirely, and with it the ACT_* tables and the job
    // executor. Configuring NOTHING is a different case. Then the single adapter type
    // on the classpath IS the configuration (see
    // withoutAnyConfigurationTheClasspathAdapterIsUsed).
    this.contextRunner
        .withPropertyValues(
            "spring.datasource.url=jdbc:h2:mem:operaton-gate-test;DB_CLOSE_DELAY=-1",
            "vanillabp.adapters.other-bpms.type=other-bpms")
        .withConfiguration(
            AutoConfigurations.of(
                org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration.class,
                org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration.class,
                OperatonAdapterConfiguration.class,
                OperatonProcessServiceConfiguration.class))
        .run(context -> {

          Assertions.assertNull(context.getStartupFailure(), "context should start");
          Assertions.assertTrue(
              context.getBeansOfType(ProcessEngine.class).isEmpty(),
              "no engine may be created without a configured operaton adapter");
          Assertions.assertTrue(
              context.getBeansOfType(OperatonDeploymentService.class).isEmpty());

          // and no ACT_* tables were created in the business database
          final var dataSource = context.getBean(javax.sql.DataSource.class);
          try (var connection = dataSource.getConnection(); var tables = connection.getMetaData().getTables(null, null,
              "ACT_%", null)) {
            Assertions.assertFalse(tables.next(), "no ACT_* tables may exist");
          }

        });

  }

  @Test
  public void withoutAnyConfigurationTheClasspathAdapterIsUsed() {

    // the adapter jar on the classpath IS the configuration. The engine of the
    // derived adapter id is created. That id is the adapter type
    this.contextRunner
        .withPropertyValues(
            "spring.datasource.url=jdbc:h2:mem:operaton-convention-test;DB_CLOSE_DELAY=-1")
        // the platform integration contributes the task invoker. It is not part of
        // this minimal runner, but the engine needs the invoker to be built
        .withBean(
            io.vanillabp.integration.adapter.spi.workflowtask.WorkflowTaskInvoker.class,
            () -> org.mockito.Mockito
                .mock(io.vanillabp.integration.adapter.spi.workflowtask.WorkflowTaskInvoker.class))
        // ...and its wiring half. The SPI is split into the two duties, and the
        // deployment service takes this one
        .withBean(
            io.vanillabp.integration.adapter.spi.workflowtask.WorkflowTaskWiring.class,
            () -> org.mockito.Mockito
                .mock(io.vanillabp.integration.adapter.spi.workflowtask.WorkflowTaskWiring.class))
        // the platform integration contributes the sync model. It is not part of
        // this minimal runner either
        .withBean(
            io.vanillabp.integration.adapter.spi.WorkflowAggregateSync.class,
            () -> org.mockito.Mockito
                .mock(io.vanillabp.integration.adapter.spi.WorkflowAggregateSync.class))
        // the name-clash-avoidance support is contributed by the platform
        // integration, too
        .withBean(
            io.vanillabp.integration.adapter.spi.NameClashAvoidanceSupport.class,
            () -> org.mockito.Mockito
                .mock(io.vanillabp.integration.adapter.spi.NameClashAvoidanceSupport.class))
        // the adapter takes the whole set of collaborators in its constructor. So a
        // runner which mocks them one by one has to name this one as well
        .withBean(
            io.vanillabp.integration.adapter.spi.PreCommitRegistrar.class,
            () -> org.mockito.Mockito.mock(io.vanillabp.integration.adapter.spi.PreCommitRegistrar.class))
        .withConfiguration(
            AutoConfigurations.of(
                org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration.class,
                org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration.class,
                OperatonAdapterConfiguration.class,
                OperatonProcessServiceConfiguration.class))
        .run(context -> {

          Assertions.assertNull(context.getStartupFailure(), "context should start");
          Assertions.assertTrue(
              context.containsBean("Operaton_Engine_operaton"),
              () -> "expected the engine of the derived adapter id 'operaton' but got the beans: "
                  + java.util.Arrays.toString(context.getBeanDefinitionNames()));

        });

  }

}
