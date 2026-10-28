![Header](./readme/vanillabp-headline.png)

# VanillaBP adapter for Operaton

![Compatible with: Operaton](https://img.shields.io/badge/Compatible%20with-Operaton-26d07c)
[![Apache License V.2](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](./LICENSE)

This is the [VanillaBP](https://www.vanillabp.io) Version 2 adapter for
[Operaton](https://operaton.org). Operaton is the open source successor of Camunda 7. It was forked
from Camunda 7 in 2024 and kept its database schema and its API. Only the packages moved, from
`org.camunda.bpm` to `org.operaton.bpm`. The engine runs embedded in the application, like Camunda 7.

If you want to **use** this adapter, read the [Wiki](https://github.com/vanillabp/operaton-adapter/wiki).
The VanillaBP concepts it builds on are explained in the
[VanillaBP Wiki](https://github.com/vanillabp/adapter-platform-integration/wiki). This `README.md` is
for contributors.

1. [How this adapter relates to the Camunda 7 adapter](#how-this-adapter-relates-to-the-camunda-7-adapter)
2. [Documentation and supported platforms](#documentation-and-supported-platforms)
3. [Coordinates](#coordinates)
4. [Configuration](#configuration)
5. [Both BPMN namespaces work](#both-bpmn-namespaces-work)
6. [Operaton's web applications](#operatons-web-applications)
7. [Supported Operaton version](#supported-operaton-version)
8. [Migrating from Camunda 7](#migrating-from-camunda-7)
9. [Known issues](#known-issues)
10. [Building](#building)
11. [Test coverage](#test-coverage)

## How this adapter relates to the Camunda 7 adapter

The code of this adapter is made from the
[Camunda 7 adapter](https://github.com/camunda-community-hub/vanillabp-camunda7-adapter) with two
OpenRewrite recipes. Operaton's own recipe renames everything which belongs to the engine. The recipe
`rewrite/vanillabp-operaton.yml` gives the adapter its own names (decision 5 in the repository's
DECISIONS.md). A few commits on top handle what Operaton does differently. The branch `generated`
holds what the recipes made, and `main` merges it. [`CONTRIBUTING.md`](./CONTRIBUTING.md) describes
how a change of the Camunda 7 adapter comes over (decision 3 in the repository's DECISIONS.md).

So this adapter behaves like the Camunda 7 adapter, with Operaton as the engine. The
[README of the Camunda 7 adapter](https://github.com/camunda-community-hub/vanillabp-camunda7-adapter#readme)
describes that behaviour section by section, and every section of it holds here: the task processing,
the outbound operations, the sharing of the workflow aggregate, the versions of a process, Quarkus in
JVM mode only, and the rest. Read "Camunda 7" there as "Operaton", `camunda7` as `operaton` and
`Camunda7...` as `Operaton...`. This README describes only what differs.

The decisions the generated code cites are those of the Camunda 7 adapter. The code says so:
`see decision 32 in the Camunda 7 adapter's DECISIONS.md`. This repository's own decisions are in
[`DECISIONS.md`](./DECISIONS.md), and its first entry says that the others hold here, too.

What differs from the Camunda 7 adapter:

|        Where        |                   Camunda 7 adapter                   |                      This adapter                       |
|---------------------|-------------------------------------------------------|---------------------------------------------------------|
| engine              | Camunda 7.24, `org.camunda.bpm`                       | Operaton 2.2, `org.operaton.bpm`                        |
| adapter type        | `camunda7`                                            | `operaton`                                              |
| artifacts           | `org.camunda.community.vanillabp:camunda7-adapter...` | `io.vanillabp:operaton-adapter...`                      |
| BPMN namespace      | `camunda:`                                            | `operaton:` and `camunda:`, see below                   |
| web applications    | `/camunda`, `camunda.bpm.*`                           | `/operaton`, `operaton.bpm.*`                           |
| expression language | Camunda's own copy of the EL API                      | the real `jakarta.el` API, EL 6                         |
| deployment source   | `camunda7:<id>`                                       | `operaton:<id>`, and `camunda7:<id>` is read as its own |

## Documentation and supported platforms

This adapter runs on both platforms VanillaBP supports:

1. **Spring Boot**<br>[![Coverage](https://img.shields.io/badge/dynamic/regex?url=https%3A%2F%2Fvanillabp.github.io%2Foperaton-adapter%2Fspring-boot-report%2Findex.html&search=Total.*%3F.([0-9]%2B)[^0-9]*%3F%25&replace=%241%25&flags=m&label=Coverage&color=green&cacheSeconds=60)](https://vanillabp.github.io/operaton-adapter/spring-boot-report)
2. **Quarkus**, in JVM mode only<br>[![Coverage](https://img.shields.io/badge/dynamic/regex?url=https%3A%2F%2Fvanillabp.github.io%2Foperaton-adapter%2Fquarkus-report%2Findex.html&search=Total.*%3F.([0-9]%2B)[^0-9]*%3F%25&replace=%241%25&flags=m&label=Coverage&color=green&cacheSeconds=60)](https://vanillabp.github.io/operaton-adapter/quarkus-report)

Coverage is measured separately per platform. The tests of one platform never cover the code of the
other. Click a badge to open its report.

The adapter builds the plain engine itself, on both platforms. It uses neither Operaton's Spring Boot
starter nor Operaton's Quarkus extension, so their versions of Spring Boot and Quarkus do not bind
the application. `operaton-engine-spring` keeps its Spring dependencies `provided`, and the adapter
runs on the Spring Boot and Quarkus versions of VanillaBP.

## Coordinates

Spring Boot:

```xml
<dependency>
  <groupId>io.vanillabp</groupId>
  <artifactId>operaton-adapter-spring-boot</artifactId>
  <version>2.0.0-SNAPSHOT</version>
</dependency>
```

Quarkus, where VanillaBP and the adapter are both extensions:

```xml
<dependency>
  <groupId>io.vanillabp</groupId>
  <artifactId>vanillabp-quarkus-integration</artifactId>
  <version>2.0.0-SNAPSHOT</version>
</dependency>
<dependency>
  <groupId>io.vanillabp</groupId>
  <artifactId>operaton-adapter-quarkus</artifactId>
  <version>2.0.0-SNAPSHOT</version>
</dependency>
```

|           Module            |                Artifact                |                        Contents                         |
|-----------------------------|----------------------------------------|---------------------------------------------------------|
| `core`                      | `operaton-adapter`                     | Platform-neutral SPI implementations and engine wiring. |
| `spring-boot`               | `operaton-adapter-spring-boot`         | Spring Boot auto-configuration registering the adapter. |
| `spring-boot-webapps`       | `operaton-adapter-spring-boot-webapps` | Optional: Operaton's Cockpit, Tasklist and Admin.       |
| `quarkus/runtime`           | `operaton-adapter-quarkus`             | The Quarkus extension.                                  |
| `quarkus/deployment`        | `operaton-adapter-quarkus-deployment`  | The build steps of the Quarkus extension.               |
| `integration-tests`         | (not published)                        | Spring Boot tests against the real engine.              |
| `quarkus/integration-tests` | (not published)                        | Quarkus tests against the real engine.                  |
| `migration-tests`           | (not published)                        | This adapter next to the Camunda 7 adapter, decision 7. |

The class names follow the Camunda 7 adapter with `Operaton` in place of `Camunda7`. An extension
which works with this adapter, such as the Business Cockpit, uses for example `OperatonAdapter`,
`OperatonProcessingContext`, `OperatonEngineFacts`, `OperatonMultiInstances`,
`OperatonTaskDefinitions`, `OperatonEngineCustomizer`, `OperatonEngineHolder`,
`OperatonQuarkusEngineRegistry`, `OperatonProcessVersions`, `OperatonTaskRegistry` and
`VanillaBpOperatonProperties`. They are in the package `io.vanillabp.operaton` and below.

## Configuration

The adapter is a VanillaBP adapter of type `operaton`. If Operaton is the only BPMS of the
application, adding the dependency is all it takes: the adapter id is then the type, `operaton`, and
the BPMN files are read from `<workflow-module-id>/processes/operaton`. To configure an instance,
give it an id and set its `type`:

```yaml
vanillabp:
  prioritized-adapters:
    - op
  adapters:
    op:
      type: operaton
      history-time-to-live: P30D
```

The keys are the ones of the Camunda 7 adapter, at `vanillabp.adapters.<id>.*`:
`database-schema-update`, `history-time-to-live`, `data-source-name`, `table-prefix`,
`serialization-format`, `engine-plugins`, `tenant-id`, `accept-unscoped-identifiers`,
`allow-listeners`, `sleep-until-something-is-due` and `db-metrics-reporting`. None of them names
Camunda, so none of them changed. The adapter reads none of Operaton's own keys (`operaton.bpm.*`,
`quarkus.operaton.*`), because it does not use Operaton's starter or extension. The
[Configuration page of the wiki](https://github.com/vanillabp/operaton-adapter/wiki/Configuration)
describes each key.

Each adapter id gets its own embedded engine, named `vanillabp-operaton-<id>`. Two engines must not
share one set of tables. That holds across adapter types, too: an `operaton` id and a `camunda7` id
on the same datasource without a `table-prefix` stop the start with a message which names both ids
(decision 47 in the Camunda 7 adapter's DECISIONS.md). `OperatonSideBySideWithCamunda7IT` tests it.

An engine plugin is named by its Operaton class, for example SPIN:

```yaml
vanillabp:
  adapters:
    op:
      serialization-format: application/json
      engine-plugins:
        spin:
          plugin-class: org.operaton.spin.plugin.impl.SpinProcessEnginePlugin
```

The dependencies are `org.operaton.bpm:operaton-engine-plugin-spin` and
`org.operaton.spin:operaton-spin-dataformat-json-jackson`. Operaton has no build of
`camunda-xstream`, so `application/xstream` needs a plugin of your own.

## Both BPMN namespaces work

Operaton has a BPMN namespace of its own, `http://operaton.org/schema/1.0/bpmn`, and the Operaton
Modeler writes it. It still reads `http://camunda.org/schema/1.0/bpmn`, the namespace of a model
written for Camunda 7. This adapter works with both, and with a model which mixes them
(decision 2 in the repository's DECISIONS.md):

- **What the adapter reads** from a model, it reads from both namespaces, the Operaton one first:
  the expression and delegate expression of a task, its topic and decision reference, the form key
  of a user task and the element variable of a multi-instance loop.
- **What the adapter writes** into a model goes into the namespace the neighbours already use. That
  is the `in` element which passes the business key to a called process, and the `property` which
  says that a call activity continues the caller's workflow aggregate. The engine reads a `camunda:`
  child only where no `operaton:` child of the same name sits under the same parent. So an
  `operaton:in` next to the modeller's `camunda:in` mappings would hide all of them, and the called
  process would get none of its variables.

The messages of the adapter still say `camunda:expression` and the like where they name an
attribute. Read `operaton:expression` where your model uses Operaton's namespace.

`OperatonNamespacesOfAModelTest` runs a model in each namespace, and one which mixes them, through
the engine.

## Operaton's web applications

The optional module `operaton-adapter-spring-boot-webapps` serves Cockpit, Tasklist and Admin at
`/operaton` against the engines this adapter built. It works like the module of the Camunda 7
adapter, with Operaton's names:

1. **Operaton's engine auto-configuration is switched off.** The module sets `operaton.bpm.enabled`
   to `false`. Operaton's starter would otherwise build a second engine on the same datasource, and
   its job executor would take the jobs of VanillaBP's engine. Setting the property to `true` stops
   the start with a message which says this.
2. **VanillaBP's engines are registered with the runtime container**, where the web applications
   look for them. The administrator the module creates gets the group `operaton-admin`.

The web application properties are Operaton's: `operaton.bpm.webapp.*`. The new web applications of
Operaton (`webapps-neo`, a technical preview in 2.2) are not part of this module.

`OperatonWebappsBootTest` and `OperatonWebappsEnvironmentPostProcessorTest` test both points. On
Quarkus there are no web applications, as with Camunda 7.

## Supported Operaton version

| Adapter version  | Operaton version it was built and tested with |
|------------------|-----------------------------------------------|
| `2.0.0-SNAPSHOT` | 2.2.0                                         |

The root POM pins Operaton in the property `operaton.version` (decision 4 in the repository's
DECISIONS.md). There is one line of this adapter, and it follows the newest Operaton minor:

- Operaton releases a minor every April and October and fixes only the newest one, with monthly
  patches. Renovate raises the patch here on its own. A new minor needs a person, and comes in within
  a month of its release.
- **Do not set `operaton.version` to another minor in your application.** The adapter uses engine
  classes under `.impl.`, and Operaton may change them in any minor without notice.
- Operaton promises that the database schema stays compatible between two minors in a row. An
  application follows the adapter from one minor to the next. The step from 2.1 to 2.2 changes no
  table; it adds one row to the schema log, which the engine writes on its first start where
  `database-schema-update` is `true`, the default.
- A nightly build runs the whole suite against the newest Operaton version on Maven Central,
  milestones and release candidates included (`.github/workflows/nightly-next-operaton.yaml`). A red
  night opens an issue labelled `nightly-next-operaton`.

Operaton 2.x is built for Spring Boot 4 and Jakarta EE 11. The 1.x line, for Spring Boot 3, is not
supported.

## Migrating from Camunda 7

There are two ways, and both keep the BPMN models as they are.

### Side by side

VanillaBP's migration feature runs two adapters next to each other: the Camunda 7 adapter keeps
serving the workflows which run on it, and new workflows start on Operaton. The application lists
both, Operaton first:

```yaml
vanillabp:
  prioritized-adapters:
    - op
    - c7
  adapters:
    op:
      type: operaton
      data-source-name: operatonDataSource
    c7:
      type: camunda7
```

The two engines need tables of their own: a datasource of their own (`data-source-name`, as above)
or a `table-prefix` of their own. On the same tables, the start stops with a message which names
both adapter ids.

`OperatonSideBySideWithCamunda7IT` runs this. The application runs twice on the same databases,
first with Camunda 7 in front, then with Operaton in front. The workflow started before the switch
finishes on Camunda 7, the new one runs on Operaton, and each engine runs a FEEL decision.

**Shared libraries.** Both engines depend on some of the same artifacts, and Maven puts only one
version of each on the classpath. Measured with `mvn dependency:tree -Dverbose` in `migration-tests`,
with Camunda 7.24.0 and Operaton 2.2.0:

|                 Artifact                 | Camunda 7.24 asks for  | Operaton 2.2 asks for |           What the migration setup uses            |
|------------------------------------------|------------------------|-----------------------|----------------------------------------------------|
| `org.camunda.feel:feel-engine`           | 1.19.3, `scala-shaded` | 1.22.1, with Scala    | 1.22.1 for Operaton, none for Camunda 7, see below |
| `org.mybatis:mybatis`                    | 3.5.15                 | 3.5.19                | 3.5.19                                             |
| `joda-time:joda-time`                    | 2.12.5                 | 2.15.0                | 2.15.0                                             |
| `com.fasterxml.uuid:java-uuid-generator` | 5.1.0                  | 5.2.0                 | 5.2.0                                              |

The newer version wins, because Camunda 7.24 gets no more releases. For mybatis, joda-time and the
UUID generator that is all: both engines run their tests with it.

The FEEL engine is different (decision 8 in the repository's DECISIONS.md). Camunda 7.24 brings it
as the jar with the classifier `scala-shaded`, Operaton 2.2 as the plain jar with Scala next to it.
They are two artifacts for Maven, so both land on the classpath, and both hold the classes of
`org.camunda.feel`. The JVM loads whichever comes first. With Operaton's first, the Camunda 7 engine
does not start (`NoSuchMethodError` in `ValueMapper$CompositeValueMapper`). With Camunda's first,
Operaton's decisions break. So an application which runs both engines does two things:

1. It removes Camunda's copy from the Camunda 7 adapter:

   ```xml
   <dependency>
     <groupId>org.camunda.community.vanillabp</groupId>
     <artifactId>camunda7-adapter-spring-boot</artifactId>
     <exclusions>
       <exclusion>
         <groupId>org.camunda.feel</groupId>
         <artifactId>feel-engine</artifactId>
       </exclusion>
     </exclusions>
   </dependency>
   ```
2. It lets the Camunda 7 engine use its older FEEL implementation, which needs no Scala:

   ```java
   @Bean
   public Camunda7EngineCustomizer camunda7WithoutScalaFeel() {
       return new Camunda7EngineCustomizer() {
           @Override
           public void customize(String adapterId, ProcessEngineConfigurationImpl configuration) {
               configuration.setDmnFeelEnableLegacyBehavior(true);
           }
       };
   }
   ```

The older implementation understands what simple decision tables use: comparisons, ranges and
lists of values. A decision which needs more of FEEL, a function or a context for example, has to
move to Operaton before the switch, or stay on a Camunda 7 application of its own until then. An
application without decisions needs neither step, but the first one does no harm.

### In place

Operaton runs on the database of Camunda 7.24 as it is, with no schema script. So an application can
also stop on Camunda 7 and start again on Operaton, on the same database:

1. Replace the dependency of the Camunda 7 adapter by the one of this adapter.
2. Change the `type` of the adapter id from `camunda7` to `operaton`. Keep the id, and keep
   `data-source-name` and `table-prefix` as they were.
3. Replace class names of Camunda in your configuration, for example the plugin class
   `org.camunda.spin.plugin.impl.SpinProcessEnginePlugin` by
   `org.operaton.spin.plugin.impl.SpinProcessEnginePlugin`.

Operaton 2.2 writes the schema version 7.25 into `ACT_GE_SCHEMA_LOG`. That is one row and no change
of a table. With `database-schema-update: true`, the default, the first start writes it. With
`false`, run Operaton's script `operaton-engine-2.2.0.jar!/org/operaton/bpm/engine/db/upgrade/<database>_engine_7.24_to_7.25.sql`
first.

The first start deploys a **new version of every process**. The deployments of the Camunda 7
adapter carry the source `camunda7:<id>`, this adapter's carry `operaton:<id>`, and the engine's
duplicate filter compares the source. Running workflows stay on their version and finish there. This
adapter reads both sources as its own, so its checks do not report the old deployments as somebody
else's (decision 6 in the repository's DECISIONS.md). `OperatonInPlaceSwitchFromCamunda7IT` starts
a workflow on the Camunda 7 adapter and finishes it here.

Data which names a Camunda class stays as it is. That is, for example, a Java object which the
engine stored serialized, or a script which names `org.camunda` classes. Operaton tracks this as
[operaton#941](https://github.com/operaton/operaton/issues/941). The adapter itself writes no class
names into the database.

## Known issues

- **The Camunda namespace is deprecated in Operaton.** Operaton marks its constant for the Camunda
  namespace as deprecated for removal, without a date. Once it is removed, a model written for
  Camunda 7 no longer works on Operaton. Each sync reads Operaton's release notes for it.
- **One FEEL engine per application.** Camunda 7.24 and Operaton 2.2 cannot both use the Scala FEEL
  engine in one application, see [Side by side](#side-by-side).
- **Web applications of both engines in one application** have not been tested. Both register REST
  resources and filters, at different paths.
- **The messages name `camunda:` attributes.** The texts come from the Camunda 7 adapter. For a model
  in Operaton's namespace, read `operaton:` instead.

## Building

You need a JDK 21 or newer, and Maven. Then:

```bash
mvn spotless:apply
mvn install
```

Use `install`, not `verify`: the Quarkus tests start the extension from the local Maven repository.
`spi-for-java` and the platform come from the snapshot repository of Maven Central. The module
`migration-tests` also needs the Camunda 7 adapter, whose snapshots need a login, see
[`CONTRIBUTING.md`](./CONTRIBUTING.md#snapshots). The engine runs embedded on H2, so the tests need
no Docker.

Every push to `main` publishes the snapshots to the snapshot repository of Maven Central, and the
coverage reports to GitHub Pages (`.github/workflows/publish-snapshots.yaml`). Two builds run every
night: one against the newest platform snapshot (`nightly-platform-snapshot.yaml`) and one against
the newest Operaton version (`nightly-next-operaton.yaml`). A red night opens an issue.

## Test coverage

`mvn install` builds one aggregated JaCoCo report per platform, into
`test-coverage-report/spring-boot/report` and `test-coverage-report/quarkus/report`. The module
`test-coverage-report/coverage-gate` fails the build below 85 percent of covered instructions per
platform. The rule is 90. Both work as in the Camunda 7 adapter, whose README says more.

## License

VanillaBP is published under the [Apache License, Version 2.0](./LICENSE). Operaton is published
under the Apache License, Version 2.0, too.
