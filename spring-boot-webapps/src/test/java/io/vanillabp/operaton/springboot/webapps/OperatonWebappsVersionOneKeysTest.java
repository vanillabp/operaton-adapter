package io.vanillabp.operaton.springboot.webapps;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.web.client.RestClient;

import io.vanillabp.integration.test.utils.SuppressOutputExtension;

/**
 * A key of Camunda's Spring Boot starter ends the start, except where something really
 * reads it. The webapps module of the Camunda 7 adapter reads <code>camunda.bpm.webapp.*</code>
 * and <code>camunda.bpm.enabled</code>. This module does not: Operaton's web applications read
 * <code>operaton.bpm.webapp.*</code>, and the module reads <code>operaton.bpm.enabled</code>. So
 * the Operaton name takes effect, and the Camunda name ends the start with this module, too.
 */
@ExtendWith(SuppressOutputExtension.class)
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.datasource.url=jdbc:h2:mem:c7-webapps-v1-keys;DB_CLOSE_DELAY=-1", "operaton.bpm.enabled=false", "operaton.bpm.webapp.application-path=/engine-ui"
    })
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
public class OperatonWebappsVersionOneKeysTest {

  @LocalServerPort
  private int port;

  @Test
  @DisplayName("The Operaton name of a key the web applications read takes effect")
  public void theOperatonNameOfAWebappsKeyTakesEffect() {

    final var entry = RestClient
        .builder()
        .baseUrl("http://localhost:%d".formatted(port))
        .build()
        .get()
        .uri("/engine-ui")
        .retrieve()
        .toBodilessEntity();

    assertTrue(entry.getStatusCode().is3xxRedirection(), () -> String.valueOf(entry.getStatusCode()));
    assertTrue(
        entry
            .getHeaders()
            .getFirst(HttpHeaders.LOCATION)
            .contains("/engine-ui/app/"),
        () -> entry
            .getHeaders()
            .getFirst(HttpHeaders.LOCATION));

  }

  @Test
  @DisplayName("The Camunda name of a key the web applications read ends the start, with this module, too")
  public void theCamundaNameOfAWebappsKeyEndsTheStart() {

    final var refused = assertThrows(
        RuntimeException.class,
        () -> new SpringApplicationBuilder(TestApplication.class)
            .web(WebApplicationType.NONE)
            .properties(
                "spring.datasource.url=jdbc:h2:mem:c7-webapps-v1-camunda-keys;DB_CLOSE_DELAY=-1",
                "camunda.bpm.webapp.application-path=/engine-ui")
            .run()
            .close());

    var cause = (Throwable) refused;
    while (cause.getCause() != null) {
      cause = cause.getCause();
    }
    final var message = cause.getMessage();
    assertTrue(
        message.contains("'camunda.bpm.webapp.application-path': Operaton's web applications read "
            + "'operaton.bpm.webapp.*' instead."),
        () -> message);

  }

}
