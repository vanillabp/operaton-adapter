package io.vanillabp.operaton.quarkus.runtime;

import java.util.Map;

/**
 * Holds the embedded engines of all configured {@code operaton} adapter ids, one
 * {@link OperatonQuarkusEngineHolder} per id. {@link OperatonEngineProducer} builds them
 * eagerly at startup. So validation fails at boot, never first at runtime. The registry is
 * closed when the application shuts down. Each holder stops its job executor before it
 * closes its engine.
 */
public class OperatonQuarkusEngineRegistry implements AutoCloseable {

  private final Map<String, OperatonQuarkusEngineHolder> enginesByAdapterId;

  /**
   * Takes a copy, so nothing adds an engine after the startup which validated them.
   *
   * @param enginesByAdapterId The engines built at startup, one per configured adapter id
   *          of this type
   */
  public OperatonQuarkusEngineRegistry(
      final Map<String, OperatonQuarkusEngineHolder> enginesByAdapterId) {

    this.enginesByAdapterId = Map.copyOf(enginesByAdapterId);

  }

  /**
   * The engine of one adapter id. Every id which is configured has one by the time anybody
   * asks, because the engines are built at startup.
   *
   * @param adapterId The adapter id
   * @return The adapter id's engine holder
   */
  public OperatonQuarkusEngineHolder engineFor(
      final String adapterId) {

    final var holder = enginesByAdapterId.get(adapterId);
    if (holder == null) {
      throw new IllegalStateException(
          "No embedded Operaton engine exists for adapter '%s' - configured ids: %s"
              .formatted(adapterId, enginesByAdapterId.keySet()));
    }
    return holder;

  }

  @Override
  public void close() {

    enginesByAdapterId
        .values()
        .forEach(OperatonQuarkusEngineHolder::close);

  }

}
