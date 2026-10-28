package io.vanillabp.operaton.wiring;

import org.operaton.bpm.model.bpmn.BpmnModelInstance;
import org.operaton.bpm.model.bpmn.impl.BpmnModelConstants;
import org.operaton.bpm.model.xml.impl.ModelInstanceImpl;
import org.operaton.bpm.model.xml.impl.util.ModelUtil;
import org.operaton.bpm.model.xml.instance.ModelElementInstance;

/**
 * The two namespaces of Operaton's BPMN extensions, read and written the way the engine reads
 * them.
 * <p>
 * Operaton has a namespace of its own, <code>http://operaton.org/schema/1.0/bpmn</code>. A
 * model drawn with the Operaton Modeler uses it. A model written for Camunda 7 uses
 * <code>http://camunda.org/schema/1.0/bpmn</code>, which Operaton still accepts as an
 * alternative. Both kinds of model have to work, and an application moving from Camunda 7
 * keeps its models as they are. Two rules follow:
 * <ul>
 * <li><b>Reading an attribute.</b> A typed getter of the model API, such as
 * <code>getOperatonExpression()</code>, reads both namespaces. Where the element type has
 * no such getter, {@link #attributeOf(ModelElementInstance, String)} does the same: the
 * Operaton namespace first, then the Camunda one. A plain
 * <code>getAttributeValueNs(...)</code> with one namespace misses every model which uses
 * the other.</li>
 * <li><b>Writing an element.</b> The engine reads a Camunda child element only where no
 * Operaton child of the same name sits under the same parent. So one
 * <code>operaton:in</code> added by this adapter hides every <code>camunda:in</code> which
 * the modeller wrote, and the called process loses its variables.
 * {@link #newChildOf(BpmnModelInstance, ModelElementInstance, Class)} therefore writes a new
 * element in the namespace its siblings already use.</li>
 * </ul>
 * Held by <code>OperatonNamespacesOfAModelTest</code>, which runs models written in either
 * namespace and in both through the engine.
 */
public final class OperatonExtensionNamespaces {

  /** Operaton's own namespace, the one a new model is written in. */
  public static final String OPERATON_NS = BpmnModelConstants.OPERATON_NS;

  /** The namespace of a model written for Camunda 7, which Operaton reads, too. */
  public static final String CAMUNDA_NS = BpmnModelConstants.CAMUNDA_NS;

  private OperatonExtensionNamespaces() {
  }

  /**
   * An extension attribute as the engine reads it: from the Operaton namespace, and from
   * the Camunda namespace where the Operaton one has no value.
   *
   * @param element The BPMN element, or <code>null</code>
   * @param attributeName The local name of the attribute, e.g. <code>delegateExpression</code>
   * @return The value, or <code>null</code> where neither namespace carries one
   */
  public static String attributeOf(
      final ModelElementInstance element,
      final String attributeName) {

    if (element == null) {
      return null;
    }
    final var value = element.getAttributeValueNs(OPERATON_NS, attributeName);
    if (value != null) {
      return value;
    }
    return element.getAttributeValueNs(CAMUNDA_NS, attributeName);

  }

  /**
   * The namespace a new extension element under the given parent belongs in. It is the
   * Camunda namespace where the parent is a Camunda element or already has a Camunda
   * child. It is the Operaton namespace otherwise, so a model without either gets
   * Operaton's own.
   *
   * @param parent The element the new one is added to
   * @return One of {@link #OPERATON_NS} and {@link #CAMUNDA_NS}
   */
  public static String namespaceOfTheSiblings(
      final ModelElementInstance parent) {

    final var parentElement = parent.getDomElement();
    if (CAMUNDA_NS.equals(parentElement.getNamespaceURI())) {
      return CAMUNDA_NS;
    }
    final var aSiblingIsCamunda = parentElement
        .getChildElements()
        .stream()
        .anyMatch(child -> CAMUNDA_NS.equals(child.getNamespaceURI()));
    return aSiblingIsCamunda
        ? CAMUNDA_NS
        : OPERATON_NS;

  }

  /**
   * Creates an extension element in the namespace its future siblings use and adds it to
   * the parent. Its attributes are set with
   * {@link #setAttribute(ModelElementInstance, String, String)}: a typed setter writes the
   * Operaton namespace, which the engine does not read on a Camunda element.
   *
   * @param <T> The type of the new element
   * @param model The model the parent belongs to
   * @param parent The element the new one is added to
   * @param type The model API type of the new element, e.g. <code>OperatonIn.class</code>
   * @return The new element, already added to the parent
   */
  public static <T extends ModelElementInstance> T newChildOf(
      final BpmnModelInstance model,
      final ModelElementInstance parent,
      final Class<T> type) {

    final var namespace = namespaceOfTheSiblings(parent);
    final var typeName = model
        .getModel()
        .getType(type)
        .getTypeName();
    final var domElement = model
        .getDocument()
        .createElement(namespace, typeName);
    final var element = type.cast(ModelUtil.getModelElement(domElement, (ModelInstanceImpl) model));
    parent.addChildElement(element);
    return element;

  }

  /**
   * Sets an attribute of an extension element in the element's own namespace. Written
   * that way, it carries no prefix, which is how the engine reads the attributes of
   * <code>in</code>, <code>out</code> and <code>property</code>.
   *
   * @param element The extension element
   * @param attributeName The local name of the attribute
   * @param value The value
   */
  public static void setAttribute(
      final ModelElementInstance element,
      final String attributeName,
      final String value) {

    element.setAttributeValueNs(
        element
            .getDomElement()
            .getNamespaceURI(),
        attributeName,
        value);

  }

}
