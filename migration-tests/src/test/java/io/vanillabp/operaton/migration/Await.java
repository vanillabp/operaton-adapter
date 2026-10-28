package io.vanillabp.operaton.migration;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.function.Supplier;

/**
 * Waits for what the job executor of an engine does after the test's own transaction.
 */
final class Await {

  private static final long TIMEOUT_MS = 30_000;

  private Await() {
  }

  static <T> T until(
      final Supplier<T> value,
      final String description) {

    final var deadline = System.currentTimeMillis() + TIMEOUT_MS;
    while (true) {
      final var current = value.get();
      if (current != null) {
        return current;
      }
      if (System.currentTimeMillis() >= deadline) {
        fail("not within %d ms: %s".formatted(TIMEOUT_MS, description));
      }
      try {
        Thread.sleep(50);
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        fail("interrupted while waiting for "
            + description);
      }
    }

  }

}
