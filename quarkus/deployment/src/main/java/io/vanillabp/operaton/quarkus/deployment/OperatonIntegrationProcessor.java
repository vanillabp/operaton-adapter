package io.vanillabp.operaton.quarkus.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.vanillabp.integration.deployment.pipeline.VanillaBpAdapterDeploymentServiceBuildItem;
import io.vanillabp.integration.deployment.processservice.VanillaBpMigratableProcessServiceBuildItem;
import io.vanillabp.operaton.OperatonAdapter;
import io.vanillabp.operaton.quarkus.deployment.config.OperatonProperties;
import io.vanillabp.operaton.quarkus.runtime.OperatonAdapterProducer;
import io.vanillabp.operaton.quarkus.runtime.OperatonEngineProducer;
import io.vanillabp.operaton.quarkus.runtime.OperatonStartupObserver;

/**
 * Quarkus extension of the VanillaBP Operaton adapter (embedded plain engine, JVM mode
 * only). It announces the adapter type, its process-service bean and its
 * deployment-service bean to the VanillaBP Quarkus integration. It also registers the
 * engine producer and the startup observer. The observer forces the engines to be built
 * at boot, and with them the configuration to be validated.
 */
class OperatonIntegrationProcessor {

  private static final String FEATURE = "vanillabp-operaton";

  /**
   * Builds the {@link VanillaBpMigratableProcessServiceBuildItem}. The VanillaBP Quarkus
   * integration uses it to find the process-service bean of the Operaton adapter.
   *
   * @param properties Operaton build-time properties (forces config-root registration)
   * @param featureProducer Feature build-item producer used to register the extension
   * @return The {@link VanillaBpMigratableProcessServiceBuildItem}
   */
  @BuildStep
  VanillaBpMigratableProcessServiceBuildItem buildProcessServices(
      final OperatonProperties properties,
      final BuildProducer<FeatureBuildItem> featureProducer) {

    featureProducer.produce(new FeatureBuildItem(FEATURE));

    return VanillaBpMigratableProcessServiceBuildItem
        .builder()
        .adapterType(OperatonAdapter.ADAPTER_TYPE)
        // the VanillaBP extension registers the announced bean class. So it has to
        // be the producer, not the core process-service class
        .migratableProcessServiceBeanClass(OperatonAdapterProducer.class.getName())
        .build();

  }

  /**
   * Builds the {@link VanillaBpAdapterDeploymentServiceBuildItem}. The VanillaBP Quarkus
   * integration uses it to find the deployment-service bean of the Operaton adapter. The
   * platform's runtime deployment pipeline uses that bean (readBpmn &rarr; prepareBpmn
   * &rarr; wireBpmn &rarr; deployResources &rarr; startWorkflowProcessing).
   *
   * @return The {@link VanillaBpAdapterDeploymentServiceBuildItem}
   */
  @BuildStep
  VanillaBpAdapterDeploymentServiceBuildItem buildDeploymentServices() {

    return VanillaBpAdapterDeploymentServiceBuildItem
        .builder()
        .adapterType(OperatonAdapter.ADAPTER_TYPE)
        .deploymentServiceBeanClass(OperatonAdapterProducer.class.getName())
        .build();

  }

  /**
   * Registers the other runtime beans: the engine producer (one embedded engine per
   * configured adapter id) and the startup observer which forces it at boot.
   *
   * @return The bean registration build item
   */
  @BuildStep
  AdditionalBeanBuildItem registerEngineProducer() {

    // the VanillaBP extension registers the process-service and deployment-service
    // producers through the build items above. Only the other beans are registered
    // here
    return AdditionalBeanBuildItem
        .builder()
        .addBeanClass(OperatonEngineProducer.class)
        .addBeanClass(OperatonStartupObserver.class)
        .setUnremovable()
        .build();

  }

  /**
   * Keeps the engine customizers of an extension alive. Nothing in the application
   * injects them. The engine holder looks them up while it builds its engine. And ArC
   * removes beans which nobody injects.
   *
   * @return What must not be removed
   */
  @BuildStep
  io.quarkus.arc.deployment.UnremovableBeanBuildItem keepEngineCustomizers() {

    return io.quarkus.arc.deployment.UnremovableBeanBuildItem
        .beanTypes(io.vanillabp.operaton.engine.OperatonEngineCustomizer.class);

  }

}
