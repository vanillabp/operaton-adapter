package io.vanillabp.operaton.migration;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

/**
 * The application of this module: both adapters on the classpath, and which of them runs is
 * the configuration of each test.
 */
@SpringBootApplication
public class MigrationTestApplication {

  /**
   * Lets the Camunda 7 engine evaluate decisions without the Scala FEEL engine. Operaton 2.2
   * brings another version of that library, and the pom.xml of this module removes Camunda's
   * copy. The older FEEL implementation of Camunda 7 covers what simple decision tables use,
   * such as the comparisons of this module's decision.
   *
   * @return The customizer of every Camunda 7 engine of the application
   */
  @Bean
  public io.vanillabp.camunda7.engine.Camunda7EngineCustomizer camunda7WithoutScalaFeel() {

    return new io.vanillabp.camunda7.engine.Camunda7EngineCustomizer() {

      @Override
      public void customize(
          final String adapterId,
          final org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl configuration) {

        configuration.setDmnFeelEnableLegacyBehavior(true);

      }

    };

  }

  /** The name of the system property which names the database of the second engine. */
  public static final String SECOND_DATABASE = "migration.second-database";

  /**
   * A datasource of the application which only an engine uses, named by
   * <code>vanillabp.adapters.&lt;id&gt;.data-source-name</code>. Out of injection by type,
   * so Spring Boot's own datasource stays the default one.
   *
   * @return A datasource on the database the system property {@link #SECOND_DATABASE} names
   */
  @Bean(defaultCandidate = false)
  public DataSource secondEngine() {

    return new SimpleDriverDataSource(
        new org.h2.Driver(), "jdbc:h2:mem:%s;DB_CLOSE_DELAY=-1"
            .formatted(System.getProperty(SECOND_DATABASE, "unused-second-engine")));

  }

}
