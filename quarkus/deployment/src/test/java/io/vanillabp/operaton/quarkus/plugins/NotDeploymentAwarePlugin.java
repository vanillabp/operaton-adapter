package io.vanillabp.operaton.quarkus.plugins;

import org.operaton.bpm.engine.impl.cfg.AbstractProcessEnginePlugin;
import org.operaton.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * An engine plugin of the application, as a CDI bean, which switches off the deployment
 * awareness the adapter sets.
 */
@ApplicationScoped
public class NotDeploymentAwarePlugin extends AbstractProcessEnginePlugin {

  @Override
  public void preInit(
      final ProcessEngineConfigurationImpl configuration) {
    configuration.setJobExecutorDeploymentAware(false);
  }

}
