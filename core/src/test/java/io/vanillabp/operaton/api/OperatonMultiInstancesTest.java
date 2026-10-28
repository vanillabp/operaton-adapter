package io.vanillabp.operaton.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vanillabp.integration.test.utils.SuppressOutputExtension;

/**
 * What {@link OperatonMultiInstances} promises: the scopes of an execution, and an empty map
 * wherever the engine holds nothing to walk. Each case here has one level only. The order
 * outermost first is held by {@code OperatonMultiInstancesAcrossCallActivitiesTest}.
 */
@ExtendWith(SuppressOutputExtension.class)
public class OperatonMultiInstancesTest {

  @Test
  @DisplayName("The scopes of a user task inside a multi-instance subprocess are reported")
  public void theScopesOfATaskInAMultiInstanceSubprocessAreReported() {

    try (var engine = AnEngineRunningTheFactsProcess.started("multi-instances")) {

      final var task = engine.waitingUserTask();
      final var scopes = OperatonMultiInstances.of(engine.processEngine(), task.getExecutionId());

      assertEquals(
          List.of(AnEngineRunningTheFactsProcess.MULTI_INSTANCE_ELEMENT_ID),
          List.copyOf(scopes.keySet()),
          "the multi-instance subprocess was not the one scope reported");
      final var scope = scopes.get(AnEngineRunningTheFactsProcess.MULTI_INSTANCE_ELEMENT_ID);
      assertNotNull(scope, "no scope for the multi-instance subprocess");
      assertEquals(
          AnEngineRunningTheFactsProcess.ORDERS.getFirst(),
          scope.element(),
          "the element variable of the first iteration");
      assertEquals(0, scope.index(), "the first iteration is index zero");
      assertEquals(
          AnEngineRunningTheFactsProcess.ORDERS.size(),
          scope.total(),
          "the total is the size of the collection the model iterates");

    }

  }

  @Test
  @DisplayName("An execution the engine does not hold yields no scopes")
  public void anExecutionTheEngineDoesNotHoldYieldsNoScopes() {

    try (var engine = AnEngineRunningTheFactsProcess.started("multi-instances-unknown")) {

      assertTrue(
          OperatonMultiInstances
              .of(engine.processEngine(), "an-execution-which-never-existed")
              .isEmpty(),
          "an unknown execution has to answer an empty map, like a completed task does");

    }

  }

  @Test
  @DisplayName("No engine and no execution yield no scopes rather than a failure")
  public void nothingToWalkYieldsNoScopes() {

    assertTrue(OperatonMultiInstances.of(null, "an-execution").isEmpty(), "no engine");
    assertTrue(
        OperatonMultiInstances
            .of(org.mockito.Mockito.mock(org.operaton.bpm.engine.ProcessEngine.class), null)
            .isEmpty(),
        "no execution id");
    assertTrue(OperatonMultiInstances.of(null).isEmpty(), "no execution");

  }

}
