package io.vanillabp.operaton.springboot;

import org.springframework.boot.autoconfigure.AutoConfiguration;

import io.vanillabp.integration.adapter.AdapterConfigurationBase;
import io.vanillabp.integration.processservice.SpringBootMigrationAdapterAutoConfiguration;
import io.vanillabp.operaton.deployment.OperatonDeploymentService;

/**
 * Announces the Operaton adapter to the VanillaBP Spring Boot integration.
 * <p>
 * This configuration must be built before the platform validates the configured adapter
 * types. That is why it says {@code before = SpringBootMigrationAdapterAutoConfiguration}.
 * It must not declare any other bean definitions, so it can be built very early.
 */
@AutoConfiguration(before = SpringBootMigrationAdapterAutoConfiguration.class)
public class OperatonAdapterConfiguration extends AdapterConfigurationBase {

  /**
   * Spring Boot builds the class while it applies the auto-configuration. That is all it
   * does. The announcement below is read from the instance.
   */
  public OperatonAdapterConfiguration() {

  }

  /**
   * The BPMS type an application writes in <code>vanillabp.adapters.&lt;id&gt;.type</code>
   * to configure this adapter.
   */
  public static final String ADAPTER_TYPE = OperatonDeploymentService.ADAPTER_TYPE;

  @Override
  public String getAdapterType() {

    return ADAPTER_TYPE;

  }

}
