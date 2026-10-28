package io.vanillabp.operaton.quarkus;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.vanillabp.integration.test.utils.SuppressOutputExtension;
import io.vanillabp.operaton.quarkus.fork.CibsevenAdapterDouble;
import io.vanillabp.operaton.quarkus.sample.TestAggregate;
import io.vanillabp.operaton.quarkus.sample.TestAggregatePersistence;
import io.vanillabp.operaton.quarkus.sample.TestWorkflowService;

/**
 * A fork of Camunda 7 uses the same tables. A <code>operaton</code> id and an id of a fork
 * adapter on the same datasource without prefixes would be two engines on one set of tables,
 * and each job executor would run the jobs of the other one. The check of one adapter type
 * cannot see that, so the platform compares the tables of every adapter id across types. The
 * tables of <code>c7</code> come from the Quarkus configuration, the ones of the fork from a
 * double.
 */
@ExtendWith(SuppressOutputExtension.class)
public class OperatonForkOnTheSameTablesTest {

  @RegisterExtension
  static final QuarkusExtensionTest extensionTest = new QuarkusExtensionTest()
      .setArchiveProducer(() -> ShrinkWrap
          .create(JavaArchive.class)
          .addClass(TestAggregate.class)
          .addClass(TestAggregatePersistence.class)
          .addClass(TestWorkflowService.class)
          .addClass(CibsevenAdapterDouble.class)
          .addAsResource("fork-same-tables/application.yaml", "application.yaml")
          .addAsResource("c7-test/processes/test-process.bpmn", "c7-test/processes/test-process.bpmn")
          .addAsResource("workflow-module-descriptor/workflow-module", "META-INF/workflow-module"))
      .assertException(throwable -> {
        final var messages = OperatonBootFailure.messagesOf(throwable);
        Assertions.assertTrue(
            messages.contains("The adapters 'c7' (type 'operaton'), 'cib' (type 'cibseven') work on the SAME "
                + "system: datasource <the application's default datasource>, table prefix <no table prefix>."),
            () -> messages);
        Assertions.assertTrue(messages.contains("for type 'operaton', 'cibseven':"), () -> messages);
        Assertions.assertTrue(messages.contains("'vanillabp.adapters.<id>.data-source-name'"), () -> messages);
        Assertions.assertTrue(messages.contains("'vanillabp.adapters.<id>.table-prefix'"), () -> messages);
      });

  @Test
  public void aOperatonIdAndAForkIdOnTheSameTablesFailTheBoot() {
    // the assertion happens on the startup exception (assertException above)
  }

}
