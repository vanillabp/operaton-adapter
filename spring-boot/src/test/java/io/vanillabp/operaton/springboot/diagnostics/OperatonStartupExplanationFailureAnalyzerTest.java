package io.vanillabp.operaton.springboot.diagnostics;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.BeanCreationException;

import io.vanillabp.integration.test.utils.SuppressOutputExtension;
import io.vanillabp.operaton.engine.OperatonStartupExplanation;

/**
 * The analyzer finds the adapter's explanation anywhere in the chain of causes and hands its
 * two parts to Spring Boot's report. That the report really shows them first is proven with a
 * booted application, in {@code OperatonVersionOneDatabaseIT}.
 */
@ExtendWith(SuppressOutputExtension.class)
public class OperatonStartupExplanationFailureAnalyzerTest {

  private final OperatonStartupExplanationFailureAnalyzer analyzer = new OperatonStartupExplanationFailureAnalyzer();

  @Test
  @DisplayName("an explanation deep in bean creation exceptions becomes description and action")
  public void theExplanationIsFoundInTheChain() {

    final var engineFailure = new IllegalStateException("historyLevel mismatch");
    final var explanation = new OperatonStartupExplanation(
        "Operaton adapter 'c7' could not start its engine.", "Set the key.", engineFailure);
    final var failure = new BeanCreationException(
        "Operaton_ProcessService_c7", "Error creating bean", new BeanCreationException(
            "Operaton_Engine_c7", "Error creating bean", explanation));

    final var analysis = analyzer.analyze(failure);

    Assertions.assertEquals("Operaton adapter 'c7' could not start its engine.", analysis.getDescription());
    Assertions.assertEquals("Set the key.", analysis.getAction());
    Assertions.assertSame(explanation, analysis.getCause());
    Assertions.assertEquals(
        "Operaton adapter 'c7' could not start its engine. Set the key.",
        explanation.getMessage());

  }

  @Test
  @DisplayName("a failure without an explanation is left to the other analyzers")
  public void otherFailuresAreNotAnalyzed() {

    Assertions.assertNull(analyzer.analyze(
        new BeanCreationException("other", "Error creating bean", new IllegalStateException("something else"))));

  }

}
