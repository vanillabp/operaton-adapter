package io.vanillabp.operaton.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.operaton.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.operaton.bpm.engine.impl.task.TaskDefinition;
import org.operaton.bpm.model.bpmn.Bpmn;
import org.operaton.bpm.model.bpmn.BpmnModelInstance;
import org.operaton.bpm.model.bpmn.instance.SubProcess;
import org.operaton.bpm.model.bpmn.instance.UserTask;

import io.vanillabp.integration.adapter.spi.NameClashAvoidance;
import io.vanillabp.integration.test.utils.SuppressOutputExtension;
import io.vanillabp.operaton.RecordingScoping;
import io.vanillabp.operaton.wiring.OperatonTaskRegistry;

/**
 * What the published types promise for the cases a running engine hardly ever produces: an
 * execution the engine holds nothing for, a multi-instance level whose counters are gone, an
 * element which cannot be named, a user task without a form key, and an engine facts object
 * which nobody configured a tenant with.
 * <p>
 * Each of these is a claim in a javadoc. A claim which nothing measures stops being true
 * without anybody noticing. The tests are written against mocks, because nothing else
 * works here. While a workflow runs, the engine does not hand out an execution without a
 * model element. That is exactly why the guard exists.
 */
@ExtendWith(SuppressOutputExtension.class)
public class OperatonApiEdgeCasesTest {

  private static final String MODULE = "loan-approval";

  private static BpmnModelInstance theFactsModel() {

    return Bpmn
        .readModelFromStream(
            OperatonApiEdgeCasesTest.class
                .getClassLoader()
                .getResourceAsStream("api/engine-facts.bpmn"));

  }

  @Test
  @DisplayName("An execution the engine holds no model element for has no multi-instance scopes")
  public void anExecutionWithoutAModelElementHasNoScopes() {

    final var execution = mock(ExecutionEntity.class);
    when(execution.getBpmnModelElementInstance()).thenReturn(null);

    assertTrue(
        OperatonMultiInstances.of(execution).isEmpty(),
        "without a model element there is nothing to walk, and that is an empty map");

  }

  @Test
  @DisplayName("A multi-instance level whose counters the engine does not hold is left out")
  public void aLevelWithoutCountersIsLeftOut() {

    final var model = theFactsModel();
    final var execution = mock(ExecutionEntity.class);
    // the engine's own model element for the multi-instance subprocess, but none of the
    // variables it keeps per iteration. That is how a level counts as unknown
    when(execution.getBpmnModelElementInstance())
        .thenReturn((SubProcess) model.getModelElementById(AnEngineRunningTheFactsProcess.MULTI_INSTANCE_ELEMENT_ID));
    when(execution.getVariable("loopCounter")).thenReturn(null);
    when(execution.getVariable("nrOfInstances")).thenReturn(null);

    assertTrue(
        OperatonMultiInstances.of(execution).isEmpty(),
        "a level without loopCounter and nrOfInstances is left out rather than guessed");

  }

  @Test
  @DisplayName("An execution whose element cannot be named ends the walk without a scope")
  public void anExecutionWhoseElementCannotBeNamedEndsTheWalk() {

    for (final var activityInstanceId : new String[]{
        null, "an-id-without-the-marker"
    }) {

      final var model = theFactsModel();
      final var parent = mock(ExecutionEntity.class);
      when(parent.getBpmnModelElementInstance()).thenReturn(null);
      when(parent.getActivityInstanceId()).thenReturn(activityInstanceId);
      when(parent.getParentId()).thenReturn(null);
      when(parent.getSuperExecution()).thenReturn(null);

      final var execution = mock(ExecutionEntity.class);
      when(execution.getBpmnModelElementInstance())
          .thenReturn((UserTask) model.getModelElementById(AnEngineRunningTheFactsProcess.USER_TASK_ID));
      when(execution.getParentId()).thenReturn("the-parent");
      when(execution.getParent()).thenReturn(parent);

      assertTrue(
          OperatonMultiInstances.of(execution).isEmpty(),
          "an execution whose element cannot be named contributes no scope (activity instance id '%s')"
              .formatted(activityInstanceId));

    }

  }

  @Test
  @DisplayName("A parsed user task without a form key has none")
  public void aParsedUserTaskWithoutAFormKeyHasNone() {

    final var taskDefinition = mock(TaskDefinition.class);
    when(taskDefinition.getFormKey()).thenReturn(null);

    assertNull(OperatonTaskDefinitions.formKeyOf(taskDefinition));
    assertNull(OperatonTaskDefinitions.formKeyOf((TaskDefinition) null));

  }

  @Test
  @DisplayName("A definition the engine does not hold has no form key rather than a failure")
  public void aDefinitionTheEngineDoesNotHoldHasNoFormKey() {

    try (var engine = AnEngineRunningTheFactsProcess.started("api-edges")) {

      assertNull(
          OperatonTaskDefinitions
              .formKeyOf(
                  engine.processEngine(),
                  "a-definition-which-never-existed",
                  AnEngineRunningTheFactsProcess.USER_TASK_ID),
          "the engine answers an unknown definition id by throwing, and that is the empty answer here");

    }

  }

  @Test
  @DisplayName("Without any configured tenant the workflow module names the tenant")
  public void withoutAnyConfiguredTenantTheWorkflowModuleNamesIt() {

    final var facts = new OperatonEngineFacts(
        "operaton", new RecordingScoping(NameClashAvoidance.BY_ADAPTER), null, new OperatonTaskRegistry());

    assertEquals(
        MODULE,
        facts.tenantIdOf(MODULE),
        "a platform handing over no tenant resolution is the same as none being configured");

  }

}
