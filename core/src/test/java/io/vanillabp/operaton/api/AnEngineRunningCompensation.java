package io.vanillabp.operaton.api;

import java.util.List;

import org.h2.jdbcx.JdbcDataSource;
import org.operaton.bpm.engine.ProcessEngine;
import org.operaton.bpm.engine.impl.cfg.StandaloneProcessEngineConfiguration;
import org.operaton.bpm.engine.impl.persistence.entity.JobEntity;

import io.vanillabp.operaton.wiring.OperatonAsyncBpmnParseListener;
import io.vanillabp.operaton.wiring.OperatonTaskCancellationListener;
import io.vanillabp.operaton.wiring.OperatonTaskRegistry;
import io.vanillabp.operaton.wiring.OperatonUserTaskEventListener;

/**
 * A real embedded engine running a process which books, pays and then compensates both. The
 * model is <code>api/compensation-facts.bpmn</code>.
 * <p>
 * Only a running engine shows whether two compensation handlers of one workflow run at the
 * same time. There are two answers. One is for the model as a modeller writes it. The other
 * is for the model as this adapter deploys it. The adapter makes every service task an
 * asynchronous, exclusive job.
 */
final class AnEngineRunningCompensation implements AutoCloseable {

  static final String BPMN_PROCESS_ID = "CompensationFactsProcess";

  private final ProcessEngine processEngine;

  private AnEngineRunningCompensation(
      final String databaseName,
      final boolean asThisAdapterDeploysIt,
      final boolean withTheJobExecutor) {

    final var dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:%s;DB_CLOSE_DELAY=-1".formatted(databaseName));
    final var configuration = new StandaloneProcessEngineConfiguration();
    configuration.setDataSource(dataSource);
    configuration.setDatabaseSchemaUpdate("true");
    configuration.setJobExecutorActivate(withTheJobExecutor);
    configuration.setProcessEngineName(databaseName);
    configuration.setHistoryTimeToLive("P1D");
    if (asThisAdapterDeploysIt) {
      // the adapter's own parse listener. So the measurement is about what a VanillaBP
      // application really runs, not about a model written for the test. The process is
      // registered the way the adapter registers a process it deployed. The listener leaves
      // a process alone which this application did not deploy
      final var registry = new OperatonTaskRegistry();
      registry.registerProcess("compensation", BPMN_PROCESS_ID, BPMN_PROCESS_ID);
      final var parseListeners = new java.util.ArrayList<org.operaton.bpm.engine.impl.bpmn.parser.BpmnParseListener>();
      parseListeners
          .add(
              new OperatonAsyncBpmnParseListener(
                  new OperatonTaskCancellationListener(null, registry), new OperatonUserTaskEventListener(null, registry)));
      configuration.setCustomPostBPMNParseListeners(parseListeners);
    }
    this.processEngine = configuration.buildProcessEngine();
    processEngine
        .getRepositoryService()
        .createDeployment()
        .addClasspathResource("api/compensation-facts.bpmn")
        .deploy();

  }

  static AnEngineRunningCompensation plain(
      final String databaseName) {

    return new AnEngineRunningCompensation(databaseName, false, false);

  }

  static AnEngineRunningCompensation asThisAdapterDeploysIt(
      final String databaseName,
      final boolean withTheJobExecutor) {

    return new AnEngineRunningCompensation(databaseName, true, withTheJobExecutor);

  }

  ProcessEngine processEngine() {

    return processEngine;

  }

  void start() {

    processEngine.getRuntimeService().startProcessInstanceByKey(BPMN_PROCESS_ID);

  }

  List<org.operaton.bpm.engine.runtime.Job> jobs() {

    return processEngine.getManagementService().createJobQuery().list();

  }

  static boolean isExclusive(
      final org.operaton.bpm.engine.runtime.Job job) {

    return ((JobEntity) job).isExclusive();

  }

  void executeJob(
      final String jobId) {

    processEngine.getManagementService().executeJob(jobId);

  }

  boolean asyncBeforeOf(
      final String activityId) {

    final var configuration = ((org.operaton.bpm.engine.impl.ProcessEngineImpl) processEngine)
        .getProcessEngineConfiguration();
    return configuration
        .getCommandExecutorTxRequired()
        .execute(commandContext -> configuration
            .getDeploymentCache()
            .findDeployedLatestProcessDefinitionByKey(BPMN_PROCESS_ID)
            .findActivity(activityId)
            .isAsyncBefore());

  }

  long runningWorkflows() {

    return processEngine.getRuntimeService().createProcessInstanceQuery().count();

  }

  @Override
  public void close() {

    processEngine.close();

  }

}
