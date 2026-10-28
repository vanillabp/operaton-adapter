package io.vanillabp.operaton.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.operaton.bpm.model.bpmn.Bpmn;
import org.operaton.bpm.model.bpmn.instance.MultiInstanceLoopCharacteristics;
import org.operaton.bpm.model.bpmn.instance.UserTask;
import org.operaton.bpm.model.xml.instance.ModelElementInstance;

import io.vanillabp.integration.adapter.spi.workflowtask.BpmnTaskSpec;
import io.vanillabp.integration.test.utils.CapturedOutput;
import io.vanillabp.integration.test.utils.SuppressOutputExtension;
import io.vanillabp.operaton.TestCollaborators;
import io.vanillabp.operaton.api.OperatonMultiInstances;
import io.vanillabp.operaton.api.OperatonTaskDefinitions;
import io.vanillabp.operaton.wiring.OperatonExtensionNamespaces;
import io.vanillabp.operaton.wiring.OperatonTaskRegistry;

/**
 * A model drawn with the Operaton Modeler, a model written for Camunda 7, and a model which
 * mixes the two, all against the real engine. See decision 2 in the repository's
 * DECISIONS.md.
 * <p>
 * Reading: the deployment reads a task's expression, delegate expression, topic and decision
 * reference, a user task's form key and a loop's element variable. Each of them is found in
 * either namespace. Writing: the call activity which passes the business key on gets its
 * <code>in</code> element in the namespace of the modeller's own mappings, and the engine
 * still hands the called process the mapped variables.
 */
@ExtendWith(SuppressOutputExtension.class)
public class OperatonNamespacesOfAModelTest {

  private static final String MODULE = "orders";

  private static final String OPERATON = "xmlns:operaton=\"http://operaton.org/schema/1.0/bpmn\"";

  private static final String CAMUNDA = "xmlns:camunda=\"http://camunda.org/schema/1.0/bpmn\"";

  /**
   * One process with every attribute the deployment reads, written with the given prefix.
   * The decision is not deployed: the deployment only has to see that the task calls one.
   */
  private static String everythingTheDeploymentReads(
      final String prefix,
      final String namespace) {

    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" %2$s id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn">
          <bpmn:process id="Everything" isExecutable="true" %1$s:historyTimeToLive="P1D">
            <bpmn:serviceTask id="ByExpression" %1$s:expression="${checkCredit}" />
            <bpmn:serviceTask id="ByDelegate" %1$s:delegateExpression="${sendLetter}" />
            <bpmn:businessRuleTask id="ByDecision" %1$s:decisionRef="rating" />
            <bpmn:userTask id="Approve" %1$s:formKey="approveLoan">
              <bpmn:multiInstanceLoopCharacteristics %1$s:collection="${applicants}" %1$s:elementVariable="applicant" />
            </bpmn:userTask>
          </bpmn:process>
        </bpmn:definitions>
        """
        .formatted(prefix, namespace);

  }

  /** The same process with each element in another namespace. */
  private static final String EVERYTHING_MIXED = """
      <?xml version="1.0" encoding="UTF-8"?>
      <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" %s %s id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn">
        <bpmn:process id="Everything" isExecutable="true" operaton:historyTimeToLive="P1D">
          <bpmn:serviceTask id="ByExpression" camunda:expression="${checkCredit}" />
          <bpmn:serviceTask id="ByDelegate" operaton:delegateExpression="${sendLetter}" />
          <bpmn:businessRuleTask id="ByDecision" camunda:decisionRef="rating" />
          <bpmn:userTask id="Approve" operaton:formKey="approveLoan">
            <bpmn:multiInstanceLoopCharacteristics camunda:collection="${applicants}" camunda:elementVariable="applicant" />
          </bpmn:userTask>
        </bpmn:process>
      </bpmn:definitions>
      """
      .formatted(OPERATON, CAMUNDA);

  @Test
  @DisplayName("A model drawn with the Operaton Modeler has all its attributes read")
  public void aModelInOperatonsNamespaceIsRead() {

    assertEverythingIsRead(everythingTheDeploymentReads("operaton", OPERATON));

  }

  @Test
  @DisplayName("A model written for Camunda 7 has all its attributes read")
  public void aModelInCamundasNamespaceIsRead() {

    assertEverythingIsRead(everythingTheDeploymentReads("camunda", CAMUNDA));

  }

  @Test
  @DisplayName("A model which mixes both namespaces has all its attributes read")
  public void aModelMixingBothNamespacesIsRead() {

    assertEverythingIsRead(EVERYTHING_MIXED);

  }

  @Test
  @DisplayName("An external task is found in Operaton's namespace, too, and named in the warning")
  public void anExternalTaskInOperatonsNamespaceIsFound(
      final CapturedOutput output) {

    final var model = """
        <?xml version="1.0" encoding="UTF-8"?>
        <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" %s id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn">
          <bpmn:process id="Everything" isExecutable="true">
            <bpmn:serviceTask id="External" operaton:topic="someTopic" />
          </bpmn:process>
        </bpmn:definitions>
        """
        .formatted(OPERATON);

    final var specs = tasksOfAHeldVersion(model);

    final var logged = output.getOut() + output.getErr();
    assertTrue(
        logged.contains("'someTopic'"),
        () -> "the topic in Operaton's namespace makes the task an external one: "
            + logged);
    assertTrue(
        specs.stream().noneMatch(spec -> "External".equals(spec.activityId())),
        "an external task is not reported as a task the application serves");

  }

  /**
   * Reads the model twice, the way the deployment reads it and through the published API,
   * and asserts that both found every attribute.
   */
  private static void assertEverythingIsRead(
      final String model) {

    final var specs = tasksOfAHeldVersion(model)
        .stream()
        .collect(Collectors.toMap(BpmnTaskSpec::activityId, Function.identity()));

    assertEquals("checkCredit", specs.get("ByExpression").taskDefinition(), "the expression of a service task");
    assertEquals("sendLetter", specs.get("ByDelegate").taskDefinition(), "the delegate expression of a service task");
    assertNull(
        specs.get("ByDecision"),
        "a business rule task which calls a decision is the engine's, so it is no task of the application");
    assertEquals("approveLoan", specs.get("Approve").taskDefinition(), "the form key of a user task");
    assertEquals(
        List.of(),
        specs.get("Approve").multiInstanceElementsWithoutAnItem(),
        "the loop names its element variable, so the user task gets its item");

    final var parsed = Bpmn.readModelFromStream(new ByteArrayInputStream(model.getBytes(StandardCharsets.UTF_8)));
    final ModelElementInstance userTask = parsed.getModelElementById("Approve");
    assertEquals("approveLoan", OperatonTaskDefinitions.formKeyOf((UserTask) userTask));
    assertEquals(
        "applicant",
        OperatonMultiInstances
            .elementVariableOf(
                parsed
                    .getModelElementsByType(MultiInstanceLoopCharacteristics.class)
                    .iterator()
                    .next()));
    assertEquals(
        "${checkCredit}",
        OperatonExtensionNamespaces.attributeOf(parsed.getModelElementById("ByExpression"), "expression"));

  }

  private static Collection<BpmnTaskSpec> tasksOfAHeldVersion(
      final String model) {

    final var service = new OperatonDeploymentService(
        "op", AnEngineHolding.theseModels("Everything", Map.of("1", model)), mock(
            OperatonWorkflowProcessingLifecycle.class), TestCollaborators
                .builder()
                .build(), new OperatonTaskRegistry());
    return service
        .processVersionCatalogOf(MODULE, "Everything")
        .tasksOfVersion(MODULE, "Everything", "1");

  }

}
