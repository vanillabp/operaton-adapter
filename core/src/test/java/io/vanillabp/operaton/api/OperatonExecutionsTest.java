package io.vanillabp.operaton.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.operaton.bpm.engine.HistoryService;
import org.operaton.bpm.engine.history.HistoricProcessInstance;
import org.operaton.bpm.engine.history.HistoricProcessInstanceQuery;
import org.operaton.bpm.engine.impl.persistence.entity.ExecutionEntity;

import io.vanillabp.integration.test.utils.SuppressOutputExtension;

/**
 * The one rule of {@link OperatonExecutions} and its one fallback: an instance nobody called
 * is its own root.
 */
@ExtendWith(SuppressOutputExtension.class)
public class OperatonExecutionsTest {

  private static HistoricProcessInstance instance(
      final String id,
      final String rootProcessInstanceId) {

    final var instance = mock(HistoricProcessInstance.class);
    when(instance.getId()).thenReturn(id);
    when(instance.getRootProcessInstanceId()).thenReturn(rootProcessInstanceId);
    return instance;

  }

  @Test
  @DisplayName("A called instance reports the root the engine recorded")
  public void aCalledInstanceReportsTheRecordedRoot() {

    assertEquals("root-1", OperatonExecutions.rootProcessInstanceIdOf(instance("called-1", "root-1")));

  }

  @Test
  @DisplayName("An instance nobody called is its own root")
  public void anInstanceNobodyCalledIsItsOwnRoot() {

    assertEquals("alone-1", OperatonExecutions.rootProcessInstanceIdOf(instance("alone-1", null)));

  }

  @Test
  @DisplayName("No instance, no root")
  public void noInstanceNoRoot() {

    assertNull(OperatonExecutions.rootProcessInstanceIdOf((HistoricProcessInstance) null));

  }

  @Test
  @DisplayName("Asked with the execution itself, the same rule applies")
  public void askedWithTheExecutionTheSameRuleApplies() {

    final var called = mock(ExecutionEntity.class);
    when(called.getRootProcessInstanceId()).thenReturn("root-3");
    assertEquals("root-3", OperatonExecutions.rootProcessInstanceIdOf(called));

    final var alone = mock(ExecutionEntity.class);
    when(alone.getRootProcessInstanceId()).thenReturn(null);
    when(alone.getProcessInstanceId()).thenReturn("alone-3");
    assertEquals(
        "alone-3",
        OperatonExecutions.rootProcessInstanceIdOf(alone),
        "an execution of an instance nobody called runs in its own root");

    assertNull(OperatonExecutions.rootProcessInstanceIdOf((ExecutionEntity) null));

  }

  @Test
  @DisplayName("Asked by id, the same rule applies")
  public void askedByIdTheSameRuleApplies() {

    final var found = instance("called-2", "root-2");
    final var query = mock(HistoricProcessInstanceQuery.class, RETURNS_SELF);
    final var historyService = mock(HistoryService.class);
    when(query.singleResult()).thenReturn(found);
    when(historyService.createHistoricProcessInstanceQuery()).thenReturn(query);

    assertEquals("root-2", OperatonExecutions.rootProcessInstanceIdOf(historyService, "called-2"));

  }

  @Test
  @DisplayName("An instance the history does not hold answers the id it was asked about")
  public void anInstanceTheHistoryDoesNotHoldAnswersTheIdItWasAskedAbout() {

    final var query = mock(HistoricProcessInstanceQuery.class, RETURNS_SELF);
    final var historyService = mock(HistoryService.class);
    when(query.singleResult()).thenReturn(null);
    when(historyService.createHistoricProcessInstanceQuery()).thenReturn(query);

    assertEquals(
        "unknown-1",
        OperatonExecutions.rootProcessInstanceIdOf(historyService, "unknown-1"),
        "an instance nobody knows anything about cannot be shown to be a called one");
    assertEquals("unknown-1", OperatonExecutions.rootProcessInstanceIdOf(null, "unknown-1"), "no history service");
    assertNull(OperatonExecutions.rootProcessInstanceIdOf(historyService, null), "no instance id");

  }

}
