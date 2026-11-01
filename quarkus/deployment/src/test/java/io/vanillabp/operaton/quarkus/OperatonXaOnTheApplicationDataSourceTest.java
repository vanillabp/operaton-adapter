package io.vanillabp.operaton.quarkus;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.vanillabp.integration.test.utils.SuppressOutputExtension;
import io.vanillabp.operaton.quarkus.runtime.OperatonQuarkusEngineRegistry;
import io.vanillabp.operaton.quarkus.sample.TestAggregate;
import io.vanillabp.operaton.quarkus.sample.TestAggregatePersistence;
import io.vanillabp.operaton.quarkus.sample.TestWorkflowService;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.UserTransaction;

/**
 * The engine of {@code c7b} runs on a named datasource without XA, and the application's
 * default datasource is an XA datasource. A transaction takes in one datasource without XA
 * next to XA datasources. So the boot goes on, and a workflow on {@code c7b} runs to its end.
 * This is the second way out which the startup message names.
 */
@ExtendWith(SuppressOutputExtension.class)
@SuppressOutputExtension.SuppressBackgroundOutput
public class OperatonXaOnTheApplicationDataSourceTest {

  @RegisterExtension
  static final QuarkusExtensionTest extensionTest = new QuarkusExtensionTest()
      .setArchiveProducer(() -> ShrinkWrap
          .create(JavaArchive.class)
          .addClass(TestAggregate.class)
          .addClass(TestAggregatePersistence.class)
          .addClass(TestWorkflowService.class)
          .addClass(OperatonWorkflowEnd.class)
          .addAsResource("xa-on-the-application-datasource/application.yaml", "application.yaml")
          .addAsResource("c7-test/processes/test-process.bpmn", "c7-test/processes/test-process.bpmn")
          .addAsResource("workflow-module-descriptor/workflow-module", "META-INF/workflow-module"));

  @Inject
  OperatonQuarkusEngineRegistry engineRegistry;

  @Inject
  EntityManager entityManager;

  @Inject
  UserTransaction userTransaction;

  @Test
  public void aWorkflowOnTheNamedDataSourceEnds() throws Exception {

    OperatonWorkflowEnd.runsToTheEnd(engineRegistry.engineFor("c7b"), entityManager, userTransaction);

  }

}
