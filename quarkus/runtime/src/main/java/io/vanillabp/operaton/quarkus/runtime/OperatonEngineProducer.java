package io.vanillabp.operaton.quarkus.runtime;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.microprofile.config.ConfigProvider;

import io.agroal.api.AgroalDataSource;
import io.smallrye.config.SmallRyeConfig;
import io.vanillabp.integration.adapter.migration.config.MigrationAdapterProperties;
import io.vanillabp.integration.adapter.migration.workflowtask.WorkflowTaskRegistry;
import io.vanillabp.operaton.OperatonAdapter;
import io.vanillabp.operaton.engine.OperatonEngineProperties;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import jakarta.transaction.TransactionManager;

/**
 * Produces the {@link OperatonQuarkusEngineRegistry}: ONE embedded engine per configured
 * {@code operaton} adapter id. The engines are built EAGERLY at startup, because the
 * {@link OperatonStartupObserver} forces this producer on {@code StartupEvent}. So
 * configuration defects show up at boot, with guiding messages:
 * <ul>
 *   <li>each id's engine runs on the application's default Agroal datasource. Or it runs
 *       on a declared NAMED datasource (<code>quarkus.datasource.&lt;name&gt;.*</code>),
 *       set in <code>vanillabp.adapters.&lt;id&gt;.data-source-name</code>. An unknown
 *       name fails the boot and lists the declared names.</li>
 *   <li>two embedded engines on one set of tables are the same engine state. More than
 *       one {@code operaton} id on the SAME datasource AND the same
 *       <code>table-prefix</code> fails the boot, naming the remedies. A distinct
 *       <code>table-prefix</code> on a shared datasource is allowed. The check is not made
 *       here but in {@code validateDistinctAdapterInstances}, which the platform's
 *       deployment pipeline calls.</li>
 * </ul>
 * The set of adapter ids comes from the platform's core properties: the adapter ids of
 * type {@code operaton}. The overlay map ({@link VanillaBpOperatonProperties}) only looks
 * up the settings of an id that is known. The
 * {@link #close(OperatonQuarkusEngineRegistry) disposer} closes the registry when the
 * application shuts down.
 */
@ApplicationScoped
public class OperatonEngineProducer {

  /**
   * Quarkus builds the bean to call the producers below. It keeps no state. What they
   * return are beans of their own, and they live as long as the application does.
   */
  public OperatonEngineProducer() {

  }

  /**
   * Builds one engine per configured adapter id, at startup and not on first use. A
   * datasource nobody declared, or a schema the engine cannot work with, is a
   * configuration defect. It belongs in the boot log, not in the first workflow.
   *
   * @param properties The platform's own configuration, which is where the adapter ids
   *          come from
   * @param transactionManager The Narayana transaction manager every engine command joins
   * @param workflowTaskRegistry What the core knows about the application's methods
   * @param dataSources The declared Agroal datasources, which an adapter id picks one of
   * @return The engines, keyed by adapter id
   */
  @Produces
  @Singleton
  public OperatonQuarkusEngineRegistry operatonEngineRegistry(
      final MigrationAdapterProperties properties,
      final TransactionManager transactionManager,
      final WorkflowTaskRegistry workflowTaskRegistry,
      @Any final Instance<AgroalDataSource> dataSources) {

    final var overlay = ConfigProvider
        .getConfig()
        .unwrap(SmallRyeConfig.class)
        .getConfigMapping(VanillaBpOperatonProperties.class);

    final var operatonAdapterIds = properties
        .adapterTypes()
        .entrySet()
        .stream()
        .filter(adapter -> OperatonAdapter.ADAPTER_TYPE.equals(adapter.getValue()))
        .map(Map.Entry::getKey)
        .sorted()
        .toList();

    final var engines = new LinkedHashMap<String, OperatonQuarkusEngineHolder>();
    operatonAdapterIds
        .forEach(adapterId -> {
          final var keys = overlay.adapters().get(adapterId);
          final var dataSourceName = (keys == null)
              ? null
              : keys.dataSourceName().orElse(null);
          final var dataSource = resolveDataSource(dataSources, adapterId, dataSourceName);
          try {
            engines.put(adapterId, new OperatonQuarkusEngineHolder(
                adapterId, toEngineProperties(
                    keys), dataSource, !io.vanillabp.operaton.engine.OperatonEngineProperties
                        .isDefaultDataSourceName(
                            dataSourceName), transactionManager, workflowTaskRegistry, workflowTaskRegistry, workflowTaskRegistry));
          } catch (final RuntimeException e) {
            throw new IllegalStateException(
                """
                    Operaton adapter '%s': building the embedded engine on datasource '%s' failed! \
                    Check the datasource configuration ('quarkus.datasource.%s') and the engine keys \
                    'vanillabp.adapters.%s.*'."""
                    .formatted(
                        adapterId,
                        dataSourceName != null
                            ? dataSourceName
                            : "<default>",
                        dataSourceName != null
                            ? dataSourceName
                                + ".*"
                            : "*",
                        adapterId), e);
          }
        });

    return new OperatonQuarkusEngineRegistry(engines);

  }

  /**
   * Stops every engine with the application. Each holder stops its job executor before it
   * closes its engine, so nothing is acquired while the engine is going away.
   *
   * @param registry The registry being disposed of
   */
  public void close(
      @Disposes final OperatonQuarkusEngineRegistry registry) {

    registry.close();

  }

  private static OperatonEngineProperties toEngineProperties(
      final VanillaBpOperatonProperties.OperatonAdapterKeys keys) {

    final var properties = new OperatonEngineProperties();
    if (keys == null) {
      return properties;
    }
    keys.databaseSchemaUpdate().ifPresent(properties::setDatabaseSchemaUpdate);
    keys.historyTimeToLive().ifPresent(properties::setHistoryTimeToLive);
    keys.tablePrefix().ifPresent(properties::setTablePrefix);
    keys.serializationFormat().ifPresent(properties::setSerializationFormat);
    keys.sleepUntilSomethingIsDue().ifPresent(properties::setSleepUntilSomethingIsDue);
    keys.dbMetricsReporting().ifPresent(properties::setDbMetricsReporting);
    // the named plugin sections are copied onto the platform-neutral model. So the core
    // builds and configures them the same way on both platforms
    properties
        .setEnginePlugins(
            keys
                .enginePlugins()
                .entrySet()
                .stream()
                .collect(
                    java.util.stream.Collectors
                        .toMap(
                            java.util.Map.Entry::getKey,
                            entry -> {
                              final var plugin = new io.vanillabp.operaton.engine.OperatonEnginePluginProperties();
                              plugin
                                  .setPluginClass(
                                      entry
                                          .getValue()
                                          .pluginClass()
                                          .orElse(null));
                              plugin
                                  .setProperties(
                                      entry
                                          .getValue()
                                          .properties());
                              return plugin;
                            })));
    return properties;

  }

  /**
   * Resolves the Agroal datasource the adapter id's engine runs on. That is the
   * application's default datasource. If <code>data-source-name</code> is configured, it
   * is the named datasource declared under <code>quarkus.datasource.&lt;name&gt;.*</code>.
   * A missing datasource fails with a guiding message.
   */
  private static AgroalDataSource resolveDataSource(
      final Instance<AgroalDataSource> dataSources,
      final String adapterId,
      final String dataSourceName) {

    // the explicit @Default literal is required. With named datasources present, an
    // unqualified select() on the @Any instance would be ambiguous.
    // several datasources declared: VanillaBP does not guess which one the engine runs
    // on. Not even the default datasource decides it, because an embedded engine writes
    // its ACT_* tables into whatever database it gets. The reserved value 'default'
    // names the default datasource explicitly.
    if (dataSourceName == null) {
      final var declaredDataSources = declaredDataSourceNames(dataSources);
      if (declaredDataSources.size() > 1) {
        throw new IllegalStateException(
            """
                Operaton adapter '%s' runs embedded and needs a database, but the application \
                declares SEVERAL datasources: %s. Name the one this adapter id runs on:
                  vanillabp.adapters.%s.data-source-name: <datasource name>
                Use the reserved value 'default' for the application's default datasource. \
                (Two adapter ids may also share one datasource if each uses its own \
                'vanillabp.adapters.<id>.table-prefix' - Camunda does not create prefixed tables, \
                so those have to exist beforehand.)"""
                .formatted(adapterId, declaredDataSources, adapterId));
      }
    }

    final Instance<AgroalDataSource> selected = io.vanillabp.operaton.engine.OperatonEngineProperties
        .isDefaultDataSourceName(dataSourceName)
            ? dataSources.select(jakarta.enterprise.inject.Default.Literal.INSTANCE)
            : dataSources.select(new io.quarkus.agroal.DataSource.DataSourceLiteral(dataSourceName));
    if (!selected.isResolvable()) {
      if (io.vanillabp.operaton.engine.OperatonEngineProperties.isDefaultDataSourceName(dataSourceName)) {
        throw new IllegalStateException(
            """
                Operaton adapter '%s' is configured ('vanillabp.adapters.%s.type: operaton') but no \
                default datasource is available. Operaton runs embedded and always needs a database - \
                configure 'quarkus.datasource.*', reference a named datasource via \
                'vanillabp.adapters.%s.data-source-name' or remove the adapter configuration."""
                .formatted(adapterId, adapterId, adapterId));
      }
      throw new IllegalStateException(
          """
              Operaton adapter '%s' references the datasource '%s' \
              ('vanillabp.adapters.%s.data-source-name') but no such datasource is declared! Declare it \
              via 'quarkus.datasource.%s.*' (named Quarkus datasources are build-time-declared). \
              Declared datasources: %s."""
              .formatted(adapterId, dataSourceName, adapterId, dataSourceName, declaredDataSourceNames(dataSources)));
    }
    return selected.get();

  }

  /**
   * The names of all declared Agroal datasources, for guiding messages. The unnamed
   * application datasource is listed as <code>&lt;default&gt;</code>.
   */
  private static List<String> declaredDataSourceNames(
      final Instance<AgroalDataSource> dataSources) {

    final var names = new LinkedList<String>();
    dataSources
        .handles()
        .forEach(handle -> handle
            .getBean()
            .getQualifiers()
            .forEach(qualifier -> {
              if (qualifier instanceof io.quarkus.agroal.DataSource dataSourceQualifier) {
                names.add(dataSourceQualifier.value());
              } else if (qualifier instanceof jakarta.enterprise.inject.Default) {
                names.add("<default>");
              }
            }));
    return names;

  }

}
