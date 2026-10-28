package io.vanillabp.operaton.springboot.engine;

import org.operaton.bpm.engine.spring.SpringExpressionManager;
import org.operaton.bpm.impl.juel.jakarta.el.CompositeELResolver;
import org.operaton.bpm.impl.juel.jakarta.el.ELResolver;
import org.springframework.context.ApplicationContext;

import io.vanillabp.integration.adapter.spi.workflowtask.WorkflowTaskInvoker;
import io.vanillabp.operaton.wiring.OperatonTaskELResolver;
import io.vanillabp.operaton.wiring.OperatonTaskRegistry;

/**
 * Camunda's Spring-bean-aware expression manager extended by VanillaBP's
 * {@link OperatonTaskELResolver}. The engine asks its process variables first, then the
 * Spring beans, and VanillaBP last. A top-level EL name which none of the others answers
 * reaches a wired <code>&#64;WorkflowTask</code> method. Version 1 had the same order.
 */
public class OperatonSpringExpressionManager extends SpringExpressionManager {

  private final OperatonTaskRegistry taskRegistry;

  private final WorkflowTaskInvoker workflowTaskInvoker;

  /**
   * Adds this adapter's resolver behind the one which resolves Spring beans. So a bean
   * answers a name before a task does, and the start refuses a task definition which a
   * bean would hide (see decision 46 in the Camunda 7 adapter's {@code DECISIONS.md}).
   *
   * @param applicationContext The application's context, which is where every name this
   *          adapter does not claim is resolved
   * @param taskRegistry What the resolver built here looks a task up in
   * @param workflowTaskInvoker Where the application's method is called
   */
  public OperatonSpringExpressionManager(
      final ApplicationContext applicationContext,
      final OperatonTaskRegistry taskRegistry,
      final WorkflowTaskInvoker workflowTaskInvoker) {

    super(applicationContext, null);
    this.taskRegistry = taskRegistry;
    this.workflowTaskInvoker = workflowTaskInvoker;

  }

  @Override
  protected ELResolver createElResolver() {

    final var compositeResolver = (CompositeELResolver) super.createElResolver();
    compositeResolver.add(new OperatonTaskELResolver(taskRegistry, workflowTaskInvoker));
    return compositeResolver;

  }

}
