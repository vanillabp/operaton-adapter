package io.vanillabp.operaton.springboot.webapps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.operaton.bpm.container.RuntimeContainerDelegate;
import org.operaton.bpm.engine.ProcessEngine;
import org.springframework.beans.factory.ObjectProvider;

import io.vanillabp.integration.test.utils.SuppressOutputExtension;
import io.vanillabp.operaton.springboot.engine.OperatonEngineHolder;
import io.vanillabp.operaton.springboot.webapps.OperatonWebappsProperties.AdapterSection;
import io.vanillabp.operaton.springboot.webapps.OperatonWebappsProperties.AdminUser;
import io.vanillabp.operaton.springboot.webapps.OperatonWebappsProperties.Webapps;

/**
 * What the registration does with the engines it is given, without booting anything.
 * Registering an engine makes it visible to the web applications. Stopping has to take
 * it back. The runtime container is JVM-wide, so an engine left behind would outlive the
 * application context that built it.
 */
@ExtendWith(SuppressOutputExtension.class)
public class OperatonWebappsRegistrationTest {

  private static final String ENGINE_NAME = "vanillabp-operaton-unit-test";

  private OperatonWebappsRegistration registration;

  @AfterEach
  public void leaveNoEngineBehind() {

    if (registration != null) {
      registration.stop();
    }

  }

  private static OperatonEngineHolder holder(
      final String adapterId,
      final ProcessEngine engine) {

    final var holder = mock(OperatonEngineHolder.class);
    when(holder.getAdapterId()).thenReturn(adapterId);
    when(holder.getProcessEngine()).thenReturn(engine);
    return holder;

  }

  @SuppressWarnings("unchecked")
  private static ObjectProvider<OperatonEngineHolder> engines(
      final OperatonEngineHolder... holders) {

    final var provider = mock(ObjectProvider.class);
    when(provider.orderedStream()).thenAnswer(invocation -> List.of(holders).stream());
    return (ObjectProvider<OperatonEngineHolder>) provider;

  }

  private static OperatonWebappsProperties properties(
      final String adapterId,
      final Webapps webapps) {

    final var section = new AdapterSection();
    section.setWebapps(webapps);
    final var properties = new OperatonWebappsProperties();
    properties.setAdapters(Map.of(adapterId, section));
    return properties;

  }

  private static ProcessEngine engine() {

    final var engine = mock(ProcessEngine.class);
    when(engine.getName()).thenReturn(ENGINE_NAME);
    return engine;

  }

  @Test
  @DisplayName("An engine is registered on start and gone again after stop")
  public void enginesAreRegisteredAndReleased() {

    registration = new OperatonWebappsRegistration(
        engines(holder("unit-test", engine())), new OperatonWebappsProperties());

    registration.start();

    assertTrue(registration.isRunning());
    assertEquals(
        ENGINE_NAME,
        RuntimeContainerDelegate.INSTANCE
            .get()
            .getProcessEngineService()
            .getProcessEngine(ENGINE_NAME)
            .getName());

    registration.stop();

    assertNull(
        RuntimeContainerDelegate.INSTANCE
            .get()
            .getProcessEngineService()
            .getProcessEngine(ENGINE_NAME));

  }

  @Test
  @DisplayName("An adapter id with the webapps switched off is not registered")
  public void disabledIdIsSkipped() {

    final var webapps = new Webapps();
    webapps.setEnabled(false);

    registration = new OperatonWebappsRegistration(
        engines(holder("unit-test", engine())), properties("unit-test", webapps));

    registration.start();

    assertNull(
        RuntimeContainerDelegate.INSTANCE
            .get()
            .getProcessEngineService()
            .getProcessEngine(ENGINE_NAME));

  }

  @Test
  @DisplayName("An administrator without a password fails the start, naming the property")
  public void administratorWithoutPasswordFails() {

    final var adminUser = new AdminUser();
    adminUser.setId("demo");
    final var webapps = new Webapps();
    webapps.setAdminUser(adminUser);

    registration = new OperatonWebappsRegistration(
        engines(holder("unit-test", engine())), properties("unit-test", webapps));

    final var exception = assertThrows(IllegalStateException.class, registration::start);

    assertTrue(
        exception.getMessage().contains("vanillabp.adapters.unit-test.webapps.admin-user.password"),
        exception::getMessage);
    assertTrue(exception.getMessage().contains("setup wizard"), exception::getMessage);

  }

  @Test
  @DisplayName("An administrator without an id fails the start, naming the property")
  public void administratorWithoutIdFails() {

    final var adminUser = new AdminUser();
    adminUser.setPassword("secret");
    final var webapps = new Webapps();
    webapps.setAdminUser(adminUser);

    registration = new OperatonWebappsRegistration(
        engines(holder("unit-test", engine())), properties("unit-test", webapps));

    final var exception = assertThrows(IllegalStateException.class, registration::start);

    assertTrue(
        exception.getMessage().contains("vanillabp.adapters.unit-test.webapps.admin-user.id"),
        exception::getMessage);
    assertTrue(
        !exception.getMessage().contains("secret"),
        "a password must never be part of a message");

  }

}
