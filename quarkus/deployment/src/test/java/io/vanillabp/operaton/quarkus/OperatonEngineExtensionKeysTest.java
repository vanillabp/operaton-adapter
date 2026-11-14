package io.vanillabp.operaton.quarkus;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.vanillabp.integration.test.utils.SuppressOutputExtension;
import io.vanillabp.operaton.quarkus.sample.TestAggregate;
import io.vanillabp.operaton.quarkus.sample.TestAggregatePersistence;
import io.vanillabp.operaton.quarkus.sample.TestWorkflowService;

/**
 * Keys of the engine's own Quarkus extension, <code>quarkus.operaton.*</code>, end the start.
 * The adapter builds the engine itself, so such a key would change nothing. Quarkus only logs a
 * warning about a <code>quarkus.*</code> key which no extension reads, and without this check
 * the application started. The message names each key and what to write instead.
 */
@ExtendWith(SuppressOutputExtension.class)
public class OperatonEngineExtensionKeysTest {

  @RegisterExtension
  static final QuarkusExtensionTest extensionTest = new QuarkusExtensionTest()
      .setArchiveProducer(() -> ShrinkWrap
          .create(JavaArchive.class)
          .addClass(TestAggregate.class)
          .addClass(TestAggregatePersistence.class)
          .addClass(TestWorkflowService.class)
          .addAsResource("engine-extension-keys/application.yaml", "application.yaml")
          .addAsResource("c7-test/processes/test-process.bpmn", "c7-test/processes/test-process.bpmn")
          .addAsResource("workflow-module-descriptor/workflow-module", "META-INF/workflow-module"))
      .assertException(throwable -> {
        final var message = OperatonBootFailure.messagesOf(throwable);
        Assertions.assertTrue(
            message.contains("The configuration has keys of the Quarkus extension of the Operaton engine "
                + "('quarkus.operaton.*')."),
            () -> "the message says whose keys these are: "
                + message);
        Assertions.assertTrue(
            message.contains("'quarkus.operaton.datasource': Write 'vanillabp.adapters.c7.data-source-name' instead."),
            () -> "the datasource of the extension has a key of the adapter: "
                + message);
        Assertions.assertTrue(
            message.contains("'quarkus.operaton.generic-config.history': Write 'vanillabp.adapters.c7.history-level' "
                + "instead."),
            () -> "a setter of the engine with a key of the adapter is named: "
                + message);
        Assertions.assertTrue(
            message.contains("'quarkus.operaton.job-executor.thread-pool.max-pool-size': VanillaBP has no key for "
                + "this."),
            () -> "a setting without a key points to a ProcessEnginePlugin bean: "
                + message);
      });

  @Test
  public void keysOfTheEngineExtensionEndTheStart() {
    // the assertion happens on the startup exception (assertException above)
  }

}
