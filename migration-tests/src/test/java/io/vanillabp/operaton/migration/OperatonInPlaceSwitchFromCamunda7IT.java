package io.vanillabp.operaton.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vanillabp.camunda7.springboot.engine.Camunda7EngineHolder;
import io.vanillabp.integration.test.utils.SuppressOutputExtension;
import io.vanillabp.operaton.springboot.engine.OperatonEngineHolder;

/**
 * An application which stops on the Camunda 7 adapter and starts again on this one, on the same
 * database and with the same adapter id. Operaton runs on the tables of Camunda 7.24 as they
 * are. See decision 6 in the repository's DECISIONS.md.
 */
@ExtendWith(SuppressOutputExtension.class)
@SuppressOutputExtension.SuppressBackgroundOutput
public class OperatonInPlaceSwitchFromCamunda7IT {

  private static final String DATABASE = "in-place-switch";

  @Test
  @DisplayName("A workflow started on Camunda 7 finishes on Operaton after the switch")
  public void aRunningWorkflowFinishesAfterTheSwitch() {

    final var camunda7 = new HashMap<String, Object>(MigrationContexts.adapter("engine", "camunda7"));
    try (var before = MigrationContexts.boot(DATABASE, camunda7)) {
      final var aggregate = new MigrationAggregate();
      aggregate.setId("switched");
      aggregate.setAmount(1000);
      before.getBean(MigrationWorkflowService.class).start(aggregate);
      Await.until(() -> MigrationWorkflowService.WAITING_USER_TASKS.get("switched"), "the user task on Camunda 7");
      assertEquals(
          1,
          before
              .getBean("Camunda7_Engine_engine", Camunda7EngineHolder.class)
              .getRuntimeService()
              .createProcessInstanceQuery()
              .processInstanceBusinessKey("switched")
              .count());
    }

    final var operaton = new HashMap<String, Object>(MigrationContexts.adapter("engine", "operaton"));
    try (var after = MigrationContexts.boot(DATABASE, operaton)) {
      final var aggregate = after.getBean(MigrationRepository.class).findById("switched").orElseThrow();
      after
          .getBean(MigrationWorkflowService.class)
          .approve(aggregate, MigrationWorkflowService.WAITING_USER_TASKS.get("switched"));

      final var engine = after.getBean("Operaton_Engine_engine", OperatonEngineHolder.class);
      final var finished = Await.until(
          () -> engine
              .getHistoryService()
              .createHistoricProcessInstanceQuery()
              .processInstanceBusinessKey("switched")
              .finished()
              .singleResult(),
          "the workflow to finish on Operaton");
      Await.until(
          () -> after.getBean(MigrationRepository.class).findById("switched").filter(MigrationAggregate::isFinished)
              .orElse(null),
          "the last task, which only Operaton can have delivered");

      final var repositoryService = engine.getRepositoryService();
      assertEquals(
          Set.of("camunda7:engine", "operaton:engine"),
          repositoryService.createDeploymentQuery().list().stream().map(deployment -> deployment.getSource())
              .collect(Collectors.toSet()),
          "the first start on Operaton deploys again, under this adapter's source");
      assertEquals(
          2,
          repositoryService.createProcessDefinitionQuery().processDefinitionKey("MigrationProcess").count(),
          "so the process has a new version");
      assertEquals(
          1,
          repositoryService.getProcessDefinition(finished.getProcessDefinitionId()).getVersion(),
          "and the running workflow finished on the version it started on");
    }

  }

}
