package io.vanillabp.operaton;

/**
 * Constants shared by all modules of the Operaton adapter.
 */
public final class OperatonAdapter {

  /**
   * The adapter type of this adapter. Configured per adapter id via
   * {@code vanillabp.adapters.<id>.type=operaton} and announced to the
   * VanillaBP platform integrations.
   */
  public static final String ADAPTER_TYPE = "operaton";

  private OperatonAdapter() {
    // constants holder
  }

}
