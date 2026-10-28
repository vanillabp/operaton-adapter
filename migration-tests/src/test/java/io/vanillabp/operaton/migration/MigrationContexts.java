package io.vanillabp.operaton.migration;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Boots the application of this module with the adapters a test asks for. A test of this
 * module boots several applications one after the other on the same databases, the way an
 * application is deployed again with a new configuration.
 */
final class MigrationContexts {

  private MigrationContexts() {
  }

  /**
   * @param database The H2 database of the application, its aggregates and its default
   *          datasource
   * @param adapters The adapter configuration, as properties below <code>vanillabp.</code>
   * @return The running application
   */
  static ConfigurableApplicationContext boot(
      final String database,
      final Map<String, Object> adapters) {

    final var properties = new LinkedHashMap<String, Object>();
    properties.put("spring.datasource.url", "jdbc:h2:mem:%s;DB_CLOSE_DELAY=-1".formatted(database));
    // the aggregates have to survive the end of one application and the start of the next
    properties.put("spring.jpa.hibernate.ddl-auto", "update");
    properties.put("spring.main.banner-mode", "off");
    properties.put("vanillabp.outbox.poll-interval", "PT0.5S");
    properties.put("vanillabp.outbox.attempt-frequency", "PT1S");
    properties.put("vanillabp.workflow-modules.migration.workflows.MigrationProcess.allow-full-sync-with-bpms", "true");
    properties.put("vanillabp.workflow-modules.migration.workflows.MigrationProcess.declared-aggregate-values[0]", "*");
    adapters.forEach((
        key,
        value) -> properties.put("vanillabp."
            + key, value));
    return new SpringApplicationBuilder(MigrationTestApplication.class)
        .properties(properties)
        .run();

  }

  /**
   * The properties of one adapter id which reads the model of this module.
   *
   * @param adapterId The adapter id
   * @param type The adapter type, <code>camunda7</code> or <code>operaton</code>
   * @return The properties below <code>vanillabp.</code>
   */
  static Map<String, Object> adapter(
      final String adapterId,
      final String type) {

    return Map
        .of(
            "adapters.%s.type".formatted(adapterId),
            type,
            "workflow-modules.migration.adapters.%s.resources-location".formatted(adapterId),
            "classpath*:migration/processes");

  }

}
