# operaton-adapter (core)

This page is for contributors. It describes the core of the VanillaBP Operaton adapter, the part
which works on every platform. Users find their documentation in
[this adapter's wiki](https://github.com/vanillabp/operaton-adapter/wiki).
The `README.md` at the repository root describes the repository for contributors.

## What lives here

Plain Java, with **no Spring or Quarkus imports**. This module implements the VanillaBP
adapter SPI (`io.vanillabp:vanillabp-adapter-spi`), the interface an adapter offers to VanillaBP.
It does so on top of the Operaton engine (`org.camunda.bpm:camunda-engine`, which also brings
`org.camunda.bpm.model:camunda-bpmn-model`). Everything specific to the BPMS (the process engine)
belongs here. The `spring-boot` module only creates these objects and registers them.

|                                 Type                                 |                                                                            Role                                                                             |
|----------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `OperatonProcessingContext`                                          | The `PC` object the deployment pipeline passes along and fills: workflow module id, the resources to deploy and the tasks to wire.                          |
| `OperatonDeploymentService`                                          | `AdapterDeploymentService<BpmnModelInstance, OperatonProcessingContext>`, one per configured adapter id.                                                    |
| `OperatonProcessService<A>`                                          | `MigratableProcessService<A>`. The runtime of one adapter id, which the core migration adapter hands its calls to.                                          |
| `OperatonWorkflowViewer`                                             | The viewer and history API on top of `RepositoryService`/`HistoryService` (definitions, BPMN XML, element history).                                         |
| `OperatonTaskRegistry` + `OperatonTaskConnectable`                   | What the deployment wiring collected: which `@WorkflowTask` serves which BPMN element.                                                                      |
| `OperatonWorkflowTaskBehavior`                                       | Runs a task through the core's `WorkflowTaskInvoker` and turns the outcome into an action (leave activity / stay open / `BpmnError`).                       |
| `OperatonTaskELResolver` + `OperatonTaskExpressionManager`           | Let BPMN expressions read the LIVE workflow aggregate, and dispatch `camunda:expression` tasks.                                                             |
| `OperatonAsyncBpmnParseListener`                                     | Sets `asyncBefore`/`asyncAfter` on service-like tasks, so every task runs in its own transaction.                                                           |
| `OperatonTaskCancellationListener` + `OperatonUserTaskEventListener` | Deliver `@TaskEvent` CREATED/CANCELED for service tasks and user tasks.                                                                                     |
| `OperatonJobExecutorLifecycle`                                       | Starts the job executor, which the whole engine shares, with the first workflow module and stops it with the last one.                                      |
| `OperatonInstanceIdentity`                                           | Decides whether two adapter ids of this type can be told apart (by datasource or by `table-prefix`), and builds the key the platform compares across types. |
| `OperatonTablePrefixSchema`                                          | The startup check for an id which runs on a `table-prefix`. Camunda creates no prefixed tables, so they have to exist already.                              |

## Adapter type vs. id

The adapter **type** is the constant `"operaton"`. Adapter **ids** come from the
configuration. The same type may be configured under several ids, for example two Operaton
engines side by side during a migration. So the deployment service and the process service
both exist once per id, not once per type.

## Operaton traits reflected here

Operaton runs embedded in the application. For INBOUND work it joins the application's local
transaction: the engine delivers a task inside its own transaction. Work which goes to the engine
takes a different path. Every operation which moves a workflow forward is planned in phase one.
After the commit, the phase-two outbox sends it to the engine, as on every other BPMS (see
decision 2 of this repository's `DECISIONS.md`). To keep workflow modules apart, the adapter uses
by default (`by-adapter`) a Camunda **tenant id** named after the workflow module. The 1:1 relation to the aggregate becomes the
Camunda **business key**.

## What is implemented

The adapter serves the whole SPI: the deployment pipeline (`readBpmn` …
`startWorkflowProcessing`), workflow start, task processing including `@TaskId` tasks and
`@TaskEvent` deliveries, user tasks, message correlation and start by message, the
probes the BPMS election uses to find out what a BPMS knows, and the viewer/history API.

There is one exception on purpose, and it fails LOUDLY, never silently:

- **external tasks (`camunda:topic`)** are not served by this adapter. VanillaBP has nothing
  yet that matches Operaton's external-task pattern. Deploying one is refused, unless the
  application marks the task with `implemented-externally=true`.

`cancelUserTask` by BPMN error is no exception. It works here (unlike Camunda 8), so nothing
is stubbed for it.

A method which cannot do its job throws, and the message names the reason. A method which
silently does nothing would hide wiring bugs. The integration tests of both platforms check what
the SPI serves at its boundary: `OperatonTaskProcessingIT` and `OperatonWorkflowLifecycleTest`
for the runtime, and `OperatonTaskWiringValidationIT` for the deployment.

## Platform version guard

`META-INF/vanillabp/adapter-operaton.properties` holds this adapter's version and its Maven
coordinates. It also holds the version of the VanillaBP platform integration the adapter was built
against (`platform.version=${adapter-platform.version}`, filled in by the resource filtering set up
in `pom.xml`). The `OperatonDeploymentService` constructor passes it to
`VanillaBpParts.requireAdapterFitsPlatform(...)`. The platform checks the same file once more
while it boots. The boot ends in two cases: the platform integration on the classpath is older
than the one this adapter was built against, or this adapter is older than the oldest one that
platform integration still serves. The message names both versions and the dependency to change.
Maven reports neither case as a conflict. A version the application manages always wins over the
version this adapter pulls in transitively, even if that means a downgrade. See
`migration-adapter/README.md`, section "Parts which do not belong together", of the VanillaBP
platform repository.
