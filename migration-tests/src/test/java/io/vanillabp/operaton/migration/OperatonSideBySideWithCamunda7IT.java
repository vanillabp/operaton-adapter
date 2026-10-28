package io.vanillabp.operaton.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.ConfigurableApplicationContext;

import io.vanillabp.camunda7.springboot.engine.Camunda7EngineHolder;
import io.vanillabp.integration.test.utils.SuppressOutputExtension;
import io.vanillabp.operaton.springboot.engine.OperatonEngineHolder;

/**
 * The migration from Camunda 7 the way VanillaBP is built for: the Camunda 7 adapter and this
 * adapter in one application, each with an engine of its own. See decision 5 in the
 * repository's DECISIONS.md.
 * <p>
 * The application runs twice on the same databases. First with Camunda 7 in front, the way it
 * ran before the migration, and it starts a workflow there. Then with Operaton in front, the
 * way it runs after the migration. It starts a new workflow, which lands on Operaton, and it
 * completes both: the old one on Camunda 7, the new one on Operaton. Both run a FEEL decision,
 * because both engines share the FEEL engine library, and only one version of it is on the
 * classpath.
 * <p>
 * Camunda 7 runs on the application's datasource, Operaton on a datasource of its own. Two
 * engines on one set of tables are refused, across the two adapter types, too.
 */
@ExtendWith(SuppressOutputExtension.class)
@SuppressOutputExtension.SuppressBackgroundOutput
public class OperatonSideBySideWithCamunda7IT {

  private static final String DATABASE = "side-by-side";

  @BeforeAll
  public static void giveOperatonADatabaseOfItsOwn() {

    System.setProperty(MigrationTestApplication.SECOND_DATABASE, "side-by-side-operaton");

  }

  @AfterAll
  public static void forgetTheSecondDatabase() {

    System.clearProperty(MigrationTestApplication.SECOND_DATABASE);

  }

  private static Map<String, Object> bothAdapters(
      final String first,
      final String second) {

    final var adapters = new HashMap<String, Object>();
    adapters.putAll(MigrationContexts.adapter("c7", "camunda7"));
    adapters.putAll(MigrationContexts.adapter("op", "operaton"));
    adapters.put("adapters.op.data-source-name", "secondEngine");
    adapters.put("prioritized-adapters[0]", first);
    adapters.put("prioritized-adapters[1]", second);
    return adapters;

  }

  @Test
  @DisplayName("A workflow started on Camunda 7 finishes there, and a new one runs on Operaton")
  public void bothEnginesServeTheirWorkflowsInOneApplication() {

    // before the migration: Camunda 7 in front
    try (var before = MigrationContexts.boot(DATABASE, bothAdapters("c7", "op"))) {
      start(before, "running-before", 500);
      assertEquals(1, camunda7(before).getRuntimeService().createProcessInstanceQuery()
          .processInstanceBusinessKey("running-before").count());
      assertEquals(0, operaton(before).getRuntimeService().createProcessInstanceQuery()
          .processInstanceBusinessKey("running-before").count());
    }

    // after the migration: Operaton in front, Camunda 7 still serves what runs on it
    try (var after = MigrationContexts.boot(DATABASE, bothAdapters("op", "c7"))) {
      start(after, "started-after", 50);
      assertEquals(1, operaton(after).getRuntimeService().createProcessInstanceQuery()
          .processInstanceBusinessKey("started-after").count());
      assertEquals(0, camunda7(after).getRuntimeService().createProcessInstanceQuery()
          .processInstanceBusinessKey("started-after").count());

      approve(after, "running-before");
      approve(after, "started-after");

      final var camunda7History = camunda7(after).getHistoryService();
      final var onCamunda7 = Await.until(
          () -> camunda7History.createHistoricProcessInstanceQuery().processInstanceBusinessKey("running-before")
              .finished().singleResult(),
          "the workflow started before the migration to finish on Camunda 7");
      assertEquals(
          "big",
          camunda7History.createHistoricVariableInstanceQuery().processInstanceId(onCamunda7.getId())
              .variableName("size").singleResult().getValue(),
          "Camunda 7 ran its FEEL decision");

      final var operatonHistory = operaton(after).getHistoryService();
      final var onOperaton = Await.until(
          () -> operatonHistory.createHistoricProcessInstanceQuery().processInstanceBusinessKey("started-after")
              .finished().singleResult(),
          "the workflow started after the migration to finish on Operaton");
      assertEquals(
          "small",
          operatonHistory.createHistoricVariableInstanceQuery().processInstanceId(onOperaton.getId())
              .variableName("size").singleResult().getValue(),
          "Operaton ran its FEEL decision");

      final var repository = after.getBean(MigrationRepository.class);
      Await.until(
          () -> repository.findById("running-before").filter(MigrationAggregate::isFinished).orElse(null),
          "the last task of the old workflow");
      Await.until(
          () -> repository.findById("started-after").filter(MigrationAggregate::isFinished).orElse(null),
          "the last task of the new workflow");
    }

  }

  @Test
  @DisplayName("A camunda7 id and an operaton id on the same tables stop the start")
  public void bothAdaptersOnTheSameTablesAreRefused() {

    final var adapters = new HashMap<String, Object>();
    adapters.putAll(MigrationContexts.adapter("c7", "camunda7"));
    adapters.putAll(MigrationContexts.adapter("op", "operaton"));
    adapters.put("prioritized-adapters[0]", "op");
    adapters.put("prioritized-adapters[1]", "c7");

    final var refused = assertThrows(
        RuntimeException.class,
        () -> MigrationContexts.boot("same-tables", adapters).close());

    var cause = (Throwable) refused;
    while (cause.getCause() != null) {
      cause = cause.getCause();
    }
    final var message = cause.getMessage();
    assertTrue(message.contains("'c7' (type 'camunda7')"), () -> message);
    assertTrue(message.contains("'op' (type 'operaton')"), () -> message);
    assertTrue(message.contains("work on the SAME system"), () -> message);
    assertTrue(message.contains("'vanillabp.adapters.<id>.data-source-name'"), () -> message);

  }

  private static void start(
      final ConfigurableApplicationContext context,
      final String id,
      final int amount) {

    final var aggregate = new MigrationAggregate();
    aggregate.setId(id);
    aggregate.setAmount(amount);
    context.getBean(MigrationWorkflowService.class).start(aggregate);
    Await.until(() -> MigrationWorkflowService.WAITING_USER_TASKS.get(id), "the user task of workflow "
        + id);

  }

  private static void approve(
      final ConfigurableApplicationContext context,
      final String id) {

    final var aggregate = context.getBean(MigrationRepository.class).findById(id).orElseThrow();
    context.getBean(MigrationWorkflowService.class).approve(aggregate,
        MigrationWorkflowService.WAITING_USER_TASKS.get(id));

  }

  private static Camunda7EngineHolder camunda7(
      final ConfigurableApplicationContext context) {

    return context.getBean("Camunda7_Engine_c7", Camunda7EngineHolder.class);

  }

  private static OperatonEngineHolder operaton(
      final ConfigurableApplicationContext context) {

    return context.getBean("Operaton_Engine_op", OperatonEngineHolder.class);

  }

}
