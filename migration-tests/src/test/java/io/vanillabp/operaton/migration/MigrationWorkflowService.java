package io.vanillabp.operaton.migration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.vanillabp.spi.process.ProcessService;
import io.vanillabp.spi.service.BpmnProcess;
import io.vanillabp.spi.service.TaskId;
import io.vanillabp.spi.service.WorkflowService;
import io.vanillabp.spi.service.WorkflowTask;

/**
 * The one workflow of this module. The same class serves it on the Camunda 7 adapter and on
 * this adapter: a workflow service knows nothing about the engine it runs on.
 */
@Service
@WorkflowService(
    workflowAggregateClass = MigrationAggregate.class,
    bpmnProcess = @BpmnProcess(bpmnProcessId = "MigrationProcess"))
public class MigrationWorkflowService {

  /** The task id of the waiting user task, per aggregate id. */
  public static final Map<String, String> WAITING_USER_TASKS = new ConcurrentHashMap<>();

  private final ProcessService<MigrationAggregate> processService;

  public MigrationWorkflowService(
      final ProcessService<MigrationAggregate> processService) {

    this.processService = processService;

  }

  @Transactional
  public MigrationAggregate start(
      final MigrationAggregate aggregate) {

    return processService.startWorkflow(aggregate);

  }

  @Transactional
  public void approve(
      final MigrationAggregate aggregate,
      final String taskId) {

    processService.completeUserTask(aggregate, taskId);

  }

  @WorkflowTask(taskDefinition = "check")
  public void check(
      final MigrationAggregate aggregate) {

    aggregate.setChecked(true);

  }

  @WorkflowTask(taskDefinition = "approve")
  public void waitForApproval(
      final MigrationAggregate aggregate,
      @TaskId final String taskId) {

    WAITING_USER_TASKS.put(aggregate.getId(), taskId);

  }

  @WorkflowTask(taskDefinition = "finish")
  public void finish(
      final MigrationAggregate aggregate) {

    aggregate.setFinished(true);

  }

}
