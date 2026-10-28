package io.vanillabp.operaton.wiring;

import org.operaton.bpm.engine.ProcessEngineException;
import org.operaton.bpm.engine.delegate.BpmnError;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.ExecutionListener;

/**
 * What the EL resolver hands the engine for a {@code camunda:executionListener} written as
 * {@code camunda:delegateExpression}. The engine resolves the expression and expects a listener
 * object. So the adapter gives it one, instead of the activity behavior a task is served with.
 * <p>
 * A listener neither completes nor leaves its element. While the listener runs, the engine is
 * in the middle of a transition of its own. So the only thing that happens here is the call
 * into the application. That is also why this is a class of its own and not a second role of
 * {@link OperatonWorkflowTaskBehavior}. A listener which could be mistaken for an activity
 * behavior would leave the element the first time somebody wired it differently.
 */
class OperatonListenerNotification implements ExecutionListener {

  private final OperatonWorkflowTaskBehavior behavior;

  private final OperatonTaskConnectable connectable;

  OperatonListenerNotification(
      final OperatonWorkflowTaskBehavior behavior,
      final OperatonTaskConnectable connectable) {

    this.behavior = behavior;
    this.connectable = connectable;

  }

  @Override
  public void notify(
      final DelegateExecution execution) throws Exception {

    invoke(behavior, connectable, execution);

  }

  /**
   * Calls the application's method for one listener notification, whichever of the two forms
   * the BPMN uses.
   * <p>
   * A {@code TaskException} of the method arrives here as a {@link BpmnError}. A TASK raises
   * that to reach an error boundary event. But a listener has no token to route, because the
   * engine is inside a transition. The error would come up as an unhandled engine exception.
   * An incident which says nothing about the cause is the worst possible answer. So the cause
   * is named instead.
   *
   * @param behavior What invokes the application's method
   * @param connectable The listener being notified
   * @param execution The current execution
   */
  static void invoke(
      final OperatonWorkflowTaskBehavior behavior,
      final OperatonTaskConnectable connectable,
      final DelegateExecution execution) {

    try {
      behavior.invokeHandler(execution);
    } catch (final BpmnError e) {
      throw new ProcessEngineException(
          """
              The @WorkflowTask method serving the listener '%s' of BPMN process '%s' (workflow \
              module '%s') threw a TaskException! A listener cannot raise a BPMN error: the engine is \
              inside a transition of its own while the listener runs and has no token to route. Model \
              the work as a task of the process where an error has to change the path."""
              .formatted(
                  connectable.taskDefinition(), connectable.bpmnProcessId(), connectable.workflowModuleId()), e);
    }

  }

}
