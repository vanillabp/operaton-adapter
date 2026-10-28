package io.vanillabp.operaton.springboot.webapps;

import org.operaton.bpm.spring.boot.starter.property.OperatonBpmProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import io.vanillabp.operaton.springboot.engine.OperatonEngineHolder;
import io.vanillabp.operaton.springboot.processservice.OperatonProcessServiceConfiguration;

/**
 * Serves Operaton's web applications at <code>/operaton</code> against the engines
 * VanillaBP built.
 *
 * <p>
 * Two things are needed for that, and neither is obvious.
 * </p>
 *
 * <ol>
 * <li>Operaton's webapp auto-configuration only applies when a {@link OperatonBpmProperties}
 * bean exists. That bean normally comes with Operaton's engine auto-configuration. This
 * module switches that one off, because it would build a second engine (see
 * {@link OperatonWebappsEnvironmentPostProcessor}). So this module adds the properties
 * bean itself. Its defaults are what the webapps need, including the path
 * <code>/operaton</code>.</li>
 * <li>The webapps find engines through the runtime container, not through beans. So
 * {@link OperatonWebappsRegistration} registers VanillaBP's engines there.</li>
 * </ol>
 */
@AutoConfiguration(after = OperatonProcessServiceConfiguration.class)
@ConditionalOnWebApplication(type = Type.SERVLET)
@EnableConfigurationProperties({
    OperatonBpmProperties.class, OperatonWebappsProperties.class
})
public class OperatonWebappsAutoConfiguration {

  /**
   * Spring Boot builds the class to read the bean method below.
   */
  public OperatonWebappsAutoConfiguration() {

  }

  /**
   * Registers VanillaBP's engines with the Operaton webapps, so Cockpit, Tasklist and Admin
   * show them. The engines are resolved lazily. They are built while the context
   * refreshes, and this bean is defined before that.
   *
   * @param engines The engines VanillaBP built, one per configured adapter id
   * @param properties Which adapter ids the webapps serve, and who the administrator is
   * @return The registration, which runs when the context is ready
   */
  @Bean
  public OperatonWebappsRegistration vanillaBpOperatonWebappsRegistration(
      final ObjectProvider<OperatonEngineHolder> engines,
      final OperatonWebappsProperties properties) {

    return new OperatonWebappsRegistration(engines, properties);

  }

}
