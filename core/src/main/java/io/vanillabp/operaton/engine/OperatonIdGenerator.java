package io.vanillabp.operaton.engine;

import java.util.Locale;

import org.operaton.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.operaton.bpm.engine.impl.persistence.StrongUuidGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * How the engine makes the ids of what it stores, for both platforms. The key is
 * <code>vanillabp.adapters.&lt;id&gt;.id-generator</code>:
 * <ul>
 *   <li><code>uuid</code>, the default: the {@link StrongUuidGenerator}. Version 1 of VanillaBP
 *       had the engine built by Camunda's Spring Boot starter, and the starter uses it. So a
 *       database of version 1 keeps one form of ids, and several engines on one database do not
 *       compete for blocks of ids. See decision 58 in the Camunda 7 adapter's DECISIONS.md.</li>
 *   <li><code>db</code>: the engine's own <code>DbIdGenerator</code>, which takes blocks of
 *       numbers from the table <code>ACT_GE_PROPERTY</code>. The engine sets it up itself where
 *       no generator is set before it is built. It needs the engine's command executor, which
 *       exists only then. That is why the adapter leaves the setting empty instead of building
 *       a <code>DbIdGenerator</code> of its own. A generator which an engine plugin sets before
 *       the engine is built has no command executor and fails on the first deployment.</li>
 * </ul>
 * Anything else ends the start with the list of the allowed values.
 */
public final class OperatonIdGenerator {

  private static final Logger log = LoggerFactory.getLogger(OperatonIdGenerator.class);

  /** The value for the {@link StrongUuidGenerator}, which is the default. */
  public static final String UUID = "uuid";

  /** The value for the engine's own <code>DbIdGenerator</code>. */
  public static final String DB = "db";

  /** The default of the key, which is what version 1 of VanillaBP had. */
  public static final String DEFAULT = UUID;

  private OperatonIdGenerator() {
    // static helper
  }

  /**
   * Hands the configured id generator to an engine which is about to be built. It is called
   * for every adapter id.
   *
   * @param adapterId The adapter id whose engine is being built
   * @param configuration The engine configuration, before the engine is built
   * @param properties The adapter id's engine settings
   * @throws OperatonStartupExplanation If the value is neither <code>uuid</code> nor
   *         <code>db</code>
   */
  public static void applyTo(
      final String adapterId,
      final ProcessEngineConfigurationImpl configuration,
      final OperatonEngineProperties properties) {

    final var configured = properties.getIdGenerator();
    final var value = configured == null
        ? ""
        : configured
            .trim()
            .toLowerCase(Locale.ROOT);
    switch (value) {
      case UUID -> configuration.setIdGenerator(new StrongUuidGenerator());
      case DB -> {
        // the engine builds the DbIdGenerator itself, with its command executor, but only
        // where no generator is set
        configuration.setIdGenerator(null);
        log
            .info(
                "Operaton[{}]: the engine makes its ids with its DbIdGenerator, as numbers taken in blocks from "
                    + "the database ('vanillabp.adapters.{}.id-generator: db').",
                adapterId,
                adapterId);
      }
      default -> throw new OperatonStartupExplanation(
          """
              Operaton adapter '%s' could not start its engine, because \
              'vanillabp.adapters.%s.id-generator' is '%s', which is not an id generator of this \
              adapter."""
              .formatted(
                  adapterId,
                  adapterId,
                  configured == null
                      ? ""
                      : configured), """
                          Use '%s' or '%s'. The default '%s' makes UUIDs, as VanillaBP 1 did. '%s' makes \
                          numbers which the engine takes in blocks from the database."""
                          .formatted(UUID, DB, DEFAULT, DB), null);
    }

  }

}
