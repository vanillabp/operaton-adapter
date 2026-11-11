package io.vanillabp.operaton.quarkus;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.vanillabp.integration.test.utils.SuppressOutputExtension;
import io.vanillabp.operaton.quarkus.plugins.NotDeploymentAwarePlugin;
import io.vanillabp.operaton.quarkus.sample.TestAggregate;
import io.vanillabp.operaton.quarkus.sample.TestAggregatePersistence;
import io.vanillabp.operaton.quarkus.sample.TestWorkflowService;

/**
 * An engine plugin of the application, a CDI bean here, which switches off the deployment
 * awareness the adapter sets. The start ends, and the message names the setting, the
 * adapter's value, the plugin by the class the developer wrote and the key which would allow
 * it. The same with the key is {@code OperatonPluginAllowedToChangeAnAdapterSettingTest}.
 */
@ExtendWith(SuppressOutputExtension.class)
@SuppressOutputExtension.SuppressBackgroundOutput
public class OperatonPluginChangingAnAdapterSettingTest {

  @RegisterExtension
  static final QuarkusExtensionTest extensionTest = new QuarkusExtensionTest()
      .setArchiveProducer(() -> ShrinkWrap
          .create(JavaArchive.class)
          .addClass(TestAggregate.class)
          .addClass(TestAggregatePersistence.class)
          .addClass(TestWorkflowService.class)
          .addClass(NotDeploymentAwarePlugin.class)
          .addAsResource("application.yaml")
          .addAsResource("c7-test/processes/test-process.bpmn", "c7-test/processes/test-process.bpmn")
          .addAsResource("workflow-module-descriptor/workflow-module", "META-INF/workflow-module"))
      .overrideRuntimeConfigKey("quarkus.datasource.jdbc.url",
          "jdbc:h2:mem:c7-plugin-changes-setting;DB_CLOSE_DELAY=-1")
      .assertException(throwable -> {
        final var message = OperatonBootFailure.messagesOf(throwable);
        Assertions
            .assertTrue(
                message.contains("Operaton adapter 'c7': an engine plugin of your application changed settings"),
                message);
        Assertions
            .assertTrue(message.contains("'jobExecutorDeploymentAware': the adapter set 'true', and the plugin "
                + NotDeploymentAwarePlugin.class.getName()
                + " changed it to 'false' in preInit"), message);
        Assertions
            .assertTrue(
                message.contains("set 'vanillabp.adapters.c7.allow-plugins-to-change-adapter-settings: true'"),
                message);
      });

  @Test
  @DisplayName("A plugin which changes a setting of the adapter ends the start")
  public void aChangedSettingEndsTheStart() {
    // the assertion happens on the startup exception (assertException above)
  }

}
