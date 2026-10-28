package io.vanillabp.operaton.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.operaton.bpm.engine.ProcessEngine;
import org.operaton.bpm.engine.delegate.DelegateTask;
import org.operaton.bpm.engine.delegate.TaskListener;
import org.operaton.bpm.engine.impl.bpmn.behavior.UserTaskActivityBehavior;
import org.operaton.bpm.engine.impl.bpmn.parser.BpmnParseListener;
import org.operaton.bpm.engine.impl.cfg.StandaloneProcessEngineConfiguration;
import org.operaton.bpm.engine.impl.pvm.process.ActivityImpl;
import org.operaton.bpm.engine.impl.pvm.process.ScopeImpl;
import org.operaton.bpm.engine.impl.util.xml.Element;
import org.operaton.bpm.model.bpmn.Bpmn;

import io.vanillabp.integration.test.utils.SuppressOutputExtension;

/**
 * What the engine hands a TASK LISTENER. The levels of a user task depend on this one thing.
 * <p>
 * This adapter tells a <code>&#64;WorkflowTask</code> method about a user task from a task
 * listener. A task listener is given a {@link DelegateTask}, not an execution. So the
 * question was whether that task can say which iteration it runs in at all. It can. The task
 * hands out the execution it hangs on. The walk starts there, the same way as on the
 * service-like side. It answers while the task is created and also while it is cancelled. So
 * the levels of a user task cost nothing but the call. That is why this adapter makes it.
 * <p>
 * This is measured against a real embedded engine. A test double says nothing about a
 * listener which runs inside the engine's own command.
 */
@ExtendWith(SuppressOutputExtension.class)
public class OperatonMultiInstancesAtAUserTaskTest {

  private static final List<String> ORDERS = List.of("first", "second");

  private static final List<String> ROUNDS = List.of("alpha", "beta", "gamma");

  /**
   * What the walk reported for every task event the engine fired, in the order the events
   * came in. One line per event: the event name, the task and the levels.
   */
  private static final List<String> REPORTED = new ArrayList<>();

  /**
   * Walks from the execution a task listener is handed and writes down what it finds. It is
   * built the way this adapter builds its own listener. That makes the answer the adapter's
   * answer.
   */
  private static class WhatTheTaskKnows implements TaskListener {

    @Override
    public void notify(
        final DelegateTask delegateTask) {

      final var levels = OperatonMultiInstances.of(delegateTask.getExecution(), null);
      REPORTED
          .add(
              "%s %s %s"
                  .formatted(delegateTask.getEventName(), delegateTask.getTaskDefinitionKey(), levels));

    }

  }

  /**
   * Attaches that listener to every user task of the deployed models. That is where
   * {@code OperatonAsyncBpmnParseListener} attaches the listener of this adapter.
   */
  private static class ListeningToEveryUserTask implements BpmnParseListener {

    @Override
    public void parseUserTask(
        final Element userTaskElement,
        final ScopeImpl scope,
        final ActivityImpl activity) {

      final var taskDefinition = ((UserTaskActivityBehavior) activity.getActivityBehavior()).getTaskDefinition();
      taskDefinition.addBuiltInTaskListener(TaskListener.EVENTNAME_CREATE, new WhatTheTaskKnows());
      taskDefinition.addBuiltInTaskListener(TaskListener.EVENTNAME_DELETE, new WhatTheTaskKnows());

    }

  }

  private static ProcessEngine anEngineWithTheModels(
      final String databaseName) {

    final var dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:%s;DB_CLOSE_DELAY=-1".formatted(databaseName));
    final var configuration = new StandaloneProcessEngineConfiguration();
    configuration.setDataSource(dataSource);
    configuration.setDatabaseSchemaUpdate("true");
    configuration.setJobExecutorActivate(false);
    configuration.setProcessEngineName(databaseName);
    configuration.setHistoryTimeToLive("P1D");
    // a list of its own, because the engine adds its own listeners to the list it is given
    configuration.setCustomPostBPMNParseListeners(new ArrayList<>(List.of(new ListeningToEveryUserTask())));
    final var engine = configuration.buildProcessEngine();
    engine
        .getRepositoryService()
        .createDeployment()
        .addModelInstance(
            "api/mi-user-tasks.bpmn",
            Bpmn
                .readModelFromStream(
                    OperatonMultiInstancesAtAUserTaskTest.class
                        .getClassLoader()
                        .getResourceAsStream("api/mi-user-tasks.bpmn")))
        .deploy();
    return engine;

  }

  @Test
  @DisplayName("A user task inside a multi-instance subprocess is told the iteration of that subprocess")
  public void aUserTaskInsideAMultiInstanceSubprocessIsToldItsIteration() {

    REPORTED.clear();
    final var engine = anEngineWithTheModels("mi-user-task-in-a-subprocess");
    try {
      engine
          .getRuntimeService()
          .startProcessInstanceByKey("MiUserTaskProcess", Map.of("orders", ORDERS));

      final var firstTask = engine.getTaskService().createTaskQuery().taskDefinitionKey("Review").singleResult();
      assertNotNull(firstTask, "the first iteration waits at the user task");
      // the second iteration follows the first, because the subprocess iterates sequentially
      engine.getTaskService().complete(firstTask.getId());
      final var secondTask = engine.getTaskService().createTaskQuery().taskDefinitionKey("Review").singleResult();
      engine.getTaskService().complete(secondTask.getId());

      assertEquals(
          List
              .of(
                  "create Review {Reviews=MultiInstanceValue[element=first, index=0, total=2]}",
                  "create Review {Reviews=MultiInstanceValue[element=second, index=1, total=2]}"),
          REPORTED,
          "both iterations are told their element, their index and the total");

    } finally {
      engine.close();
    }

  }

  @Test
  @DisplayName("A multi-instance user task is told its own iteration, while it is created and while it is cancelled")
  public void aMultiInstanceUserTaskIsToldItsOwnIteration() {

    REPORTED.clear();
    final var engine = anEngineWithTheModels("mi-user-task-itself");
    try {
      final var workflowId = engine
          .getRuntimeService()
          .startProcessInstanceByKey("MiUserTaskItselfProcess", Map.of("orders", ROUNDS))
          .getId();

      final var task = engine.getTaskService().createTaskQuery().taskDefinitionKey("ReviewEach").singleResult();
      assertNotNull(task, "the first round waits at the user task");
      // cancel the whole workflow. A CANCELED notification comes with such a cancellation
      engine.getRuntimeService().deleteProcessInstance(workflowId, "the test is done with it");

      assertEquals(
          List
              .of(
                  "create ReviewEach {ReviewEach=MultiInstanceValue[element=alpha, index=0, total=3]}",
                  "delete ReviewEach {ReviewEach=MultiInstanceValue[element=alpha, index=0, total=3]}"),
          REPORTED,
          "the element carrying the characteristics is the level, and a task going away still "
              + "knows it");

    } finally {
      engine.close();
    }

  }

}
