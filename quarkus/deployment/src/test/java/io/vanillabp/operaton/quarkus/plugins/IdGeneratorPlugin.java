package io.vanillabp.operaton.quarkus.plugins;

import java.util.UUID;

import org.operaton.bpm.engine.impl.cfg.AbstractProcessEnginePlugin;
import org.operaton.bpm.engine.impl.cfg.IdGenerator;
import org.operaton.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;

/**
 * An engine plugin of the application, configured under {@code engine-plugins}, which
 * replaces the id generator the adapter sets. The id generator has a key of its own, so a
 * plugin may replace it without the key which allows plugins to change adapter settings.
 */
public class IdGeneratorPlugin extends AbstractProcessEnginePlugin {

  /** Ids the application wants, here UUIDs with a prefix. */
  public static class PrefixedIdGenerator implements IdGenerator {

    @Override
    public String getNextId() {
      return "app-%s".formatted(UUID.randomUUID());
    }

  }

  @Override
  public void preInit(
      final ProcessEngineConfigurationImpl configuration) {
    configuration.setIdGenerator(new PrefixedIdGenerator());
  }

}
