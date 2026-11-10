package io.vanillabp.operaton.springboot.processservice;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindHandlerAdvisor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import io.vanillabp.operaton.springboot.OperatonAdapterBeanRegistrar;
import io.vanillabp.operaton.springboot.OperatonEmptySections;
import io.vanillabp.operaton.springboot.VanillaBpOperatonProperties;

/**
 * Wires the beans of the Operaton adapter per adapter id. The imported
 * {@link OperatonAdapterBeanRegistrar} registers them. For each configured adapter id of
 * type {@code operaton} there is ONE embedded engine ({@code OperatonEngineHolder}), ONE
 * process-service <i>element</i> bean and ONE deployment-service <i>element</i> bean.
 * Without a configured {@code operaton} adapter id no bean is registered. The registrar
 * reads the set of ids from the configuration. So no {@code @Conditional} is needed here.
 * The order relative to the datasource and transaction-manager auto-configurations does
 * not matter either. The engine holders resolve the application's {@code DataSource} and
 * {@code PlatformTransactionManager} LAZILY, when the bean is created (context refresh).
 * If none is available, they fail with a guiding message.
 */
@AutoConfiguration
@EnableConfigurationProperties(VanillaBpOperatonProperties.class)
@Import(OperatonAdapterBeanRegistrar.class)
public class OperatonProcessServiceConfiguration {

  /**
   * Spring Boot builds the class to apply the annotations above. The beans of this
   * adapter are registered per configured adapter id, and only the registrar can do that.
   */
  public OperatonProcessServiceConfiguration() {

  }

  /**
   * Keeps the keys which other property sources set below a section of this adapter
   * written as <code>{}</code> in a YAML file. Without it, the empty section hides them.
   *
   * @return The advisor Spring Boot applies when it binds {@link VanillaBpOperatonProperties}
   * @see OperatonEmptySections
   */
  @Bean
  public static ConfigurationPropertiesBindHandlerAdvisor operatonEmptySectionsKeepTheirKeys() {

    return OperatonEmptySections.advisor();

  }

}
