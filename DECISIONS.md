# Decision log

The code of this repository points at the decisions below. A number is handed out once. It is never
reused or renumbered, so a citation always finds its entry. A decision which gets overturned keeps
its entry. The entry is marked as superseded and names the entry which replaced it.

Most of the code here is made from the Camunda 7 adapter (decision 3). That code cites the decisions
of the Camunda 7 adapter, and it says so: `see decision 7 in the Camunda 7 adapter's DECISIONS.md`.
Code written for this repository cites the entries below, in the usual form
`see decision 2 in the repository's DECISIONS.md`. `bin/check-decision-citations.sh` reads only the
second form, so it checks the entries of this file and nothing else.

The links below point into this repository's [`README.md`](./README.md). The README has the details
an entry leaves out on purpose.

### 1. The decisions of the Camunda 7 adapter apply here, unless an entry below says otherwise

This adapter does what the Camunda 7 adapter does, with Operaton as the engine. Its code is made
from the Camunda 7 code, and the reasons for that code are written down in the
[decision log of the Camunda 7 adapter](https://github.com/vanillabp/camunda7-adapter/blob/main/DECISIONS.md).
They hold here as well. Copying them would give two texts which drift apart, so they stay there.

An entry below is about one of two things. Either Operaton differs from Camunda 7, or this
repository differs from the Camunda 7 repository. Where an entry below contradicts a decision of the
Camunda 7 adapter, the entry below wins in this repository.

A change to a decision of the Camunda 7 adapter reaches this repository with the next sync
(decision 3). Whoever runs the sync reads the new entries of that log and asks whether one of them
needs an entry here.

### 2. Both namespaces are read, and a new extension element goes into the namespace of its siblings

Operaton has a BPMN namespace of its own, `http://operaton.org/schema/1.0/bpmn`. The Operaton
Modeler writes it. A model written for Camunda 7 uses `http://camunda.org/schema/1.0/bpmn`, and
Operaton still reads it as an alternative. An application which moves from Camunda 7 keeps its
models, and a new model is drawn with the Operaton Modeler. So both kinds of model must work, and a
model may even mix them.

**Reading.** An attribute is read with the typed getter of the model API where there is one, for
example `getOperatonExpression()`. The typed getters read both namespaces. Where the element type
has no typed getter, `OperatonExtensionNamespaces.attributeOf` reads the Operaton namespace first
and the Camunda namespace second, which is what the engine does. A plain
`getAttributeValueNs(...)` with one namespace is not used: it misses every model written in the
other one. The Camunda 7 adapter used such reads in a few places, and the fixes on top of the
generated code replace them.

**Writing.** The engine reads a Camunda child element only where no Operaton child of the same name
sits under the same parent. The adapter adds a `camunda:in` for the business key and a
`camunda:property` to some call activities. Written as `operaton:in`, it would hide every
`camunda:in` which the modeller wrote, and the called process would lose its variables. So
`OperatonExtensionNamespaces.newChildOf` writes a new element in the namespace which its siblings
already use, and Operaton's own namespace where there are none.

`OperatonNamespacesOfAModelTest` runs a model in each namespace and a model which mixes them
through the engine. See [Both BPMN namespaces work](./README.md#both-bpmn-namespaces-work).

### 3. The code is made from the Camunda 7 adapter, and a merge brings each Camunda 7 change over

The Camunda 7 adapter keeps changing, and nearly all of its changes are wanted here too. Porting
each of them by hand would cost the most and would miss some. So the code is made again from a
commit of the Camunda 7 adapter whenever it is to follow:

- `bin/sync-from-camunda7.sh` copies the Camunda 7 tree, runs Operaton's OpenRewrite recipe over
  it, then runs the recipe `rewrite/vanillabp-operaton.yml` for VanillaBP's own names (decision 5).
  It commits the result to the branch `generated` as `generated from C7 <sha>`.
- `generated` holds nothing else. `main` merges it. Everything this repository does differently
  sits on `main` as ordinary commits on top, so a conflict appears only where a Camunda 7 change
  touches one of those places.
- Files which `main` writes itself are never copied: the workflows, `README.md`, `DECISIONS.md`,
  `AGENTS.md`, `CONTRIBUTING.md`, `CLAUDE.md`, `UPGRADE.md` and `renovate.json`. The script lists
  them.

The recipes rename symbols reliably. What they cannot see is behaviour: a new raw namespace read, a
new extension element the adapter writes, a Camunda name in a string, the source of a deployment.
So each sync ends with a search of the Camunda 7 diff for those four kinds of places.
`CONTRIBUTING.md` holds the procedure and the search.

A test class of the Camunda 7 adapter whose name starts with `C7`, such as `C7E2eAggregate`, keeps
its name. Only classes named `Camunda7...` are renamed. A test class name is read by nobody outside
its module, and every renamed name is one more line which can conflict in a merge.

### 4. One Operaton minor is pinned, and the adapter follows the newest one

The root POM pins Operaton in the property `operaton.version`. There is one line of this adapter,
and its version follows VanillaBP, not Operaton.

Operaton releases a minor every April and October. It fixes only the newest minor, with monthly
patches, for six months. The engine runs inside the application, so an application gets an engine
fix by updating the adapter. There is no cluster to upgrade, unlike Camunda 8, so there is no reason
for release lines. And the database schema stays compatible between two minors in a row. So:

- Renovate raises the patch on its own. A new minor needs a person and comes in within a month of
  its release. The recipe file pins the same minor, so a sync does not change the line.
- An application must not set `operaton.version` to another minor. The adapter uses engine classes
  under `.impl.`, which Operaton may change in any minor without notice.
- The nightly workflow `nightly-next-operaton.yaml` builds against the newest Operaton version on
  Maven Central, milestones and release candidates included. A red night opens an issue.

If Operaton ever starts a minor with long-term support, this decision is to be looked at again.
See [Supported Operaton version](./README.md#supported-operaton-version).

### 5. The adapter has a name of its own everywhere: type, package, artifacts and capability

The adapter must be able to run next to the Camunda 7 adapter in one application. That is how an
application moves from Camunda 7 to Operaton. Two classes with the same name on one classpath, or
two Quarkus extensions with the same capability, break that. So nothing of this adapter carries the
name of the Camunda 7 adapter:

|         What          |           Camunda 7 adapter            |              This adapter              |
|-----------------------|----------------------------------------|----------------------------------------|
| adapter type          | `camunda7`                             | `operaton`                             |
| Maven groupId         | `org.camunda.community.vanillabp`      | `io.vanillabp`                         |
| artifactIds           | `camunda7-adapter...`                  | `operaton-adapter...`                  |
| Java package          | `io.vanillabp.camunda7`                | `io.vanillabp.operaton`                |
| class names           | `Camunda7...`                          | `Operaton...`                          |
| Quarkus extension     | `vanillabp-camunda7`                   | `vanillabp-operaton`                   |
| Quarkus capability    | `io.vanillabp.adapter.camunda7`        | `io.vanillabp.adapter.operaton`        |
| engine and bean names | `vanillabp-camunda7-<id>`, `Camunda7_` | `vanillabp-operaton-<id>`, `Operaton_` |

The groupId is not the one of the Camunda Community Hub, because Operaton is not a Camunda project.
The recipe `rewrite/vanillabp-operaton.yml` writes all of these names, so a sync keeps them.
`OperatonSideBySideWithCamunda7IT` runs both adapters in one application.

### 6. A deployment of the Camunda 7 adapter counts as one of this adapter's own

Operaton runs on the database of Camunda 7.24 as it is. So an application can stop on Camunda 7
and start again on Operaton, on the same database. Both adapters write the source
`<adapter id>:<workflow module id>` into a deployment (decision 17 in the Camunda 7 adapter's
DECISIONS.md). A check tells the deployments of a VanillaBP adapter from those of somebody else by
that form. So a deployment of the Camunda 7 adapter counts as this adapter's own, and a check which
reports who else holds a process does not blame the application's own earlier deployments.

The first start after the switch keeps the adapter id, so it writes the same source. Both adapters
prepare a model the same way, so the XML is the same as well. The engine's duplicate filter compares
both and deploys nothing new. Running workflows go on, on the deployment the Camunda 7 adapter made.
`OperatonInPlaceSwitchFromCamunda7IT` starts a workflow on the Camunda 7 adapter and finishes it on
this one. See [Migrating from Camunda 7](./README.md#migrating-from-camunda-7).

*Changed on 2026-11-10 by the sync of Camunda 7 commit 10b7bad. Before, the Camunda 7 adapter wrote
`camunda7:<adapter id>` and this adapter `operaton:<adapter id>`. So this adapter read both marks as
its own, and the first start after the switch deployed a new version of every process. The Camunda 7
adapter changed the source in its decision 17. Now both adapters write the same source, so the second
mark and the new version are gone. What the entry decides stays the same.*

### 7. The tests with the Camunda 7 adapter are a module of their own

Two tests run the Camunda 7 adapter in the same application as this adapter: the side-by-side
migration and the switch of a database. They need the Camunda 7 adapter on the classpath. They live
in the module `migration-tests`, and not in `integration-tests`, for two reasons:

- `integration-tests` comes from the Camunda 7 adapter. Its POM and its `application.yaml` change
  with every sync, and a module of this repository's own never meets a conflict there.
- The Camunda 7 adapter of that module is a snapshot, and its snapshots are only in the GitHub
  Packages registry of the Camunda Community Hub, which wants a login even for reading. A module of
  its own keeps that need to one place. Every module before it builds without the login.

The module names that registry under the id `vanillabp-camunda7-adapter`. A
build needs a server entry with that id and a token with `read:packages`. Once the Camunda 7 adapter
has a release on Maven Central, the module uses it and the registry goes away. The Spring Boot
coverage report reads the module's execution data.

### 8. Next to Operaton 2.2, the Camunda 7 engine evaluates decisions without the Scala FEEL engine

Camunda 7.24 brings the FEEL engine as `org.camunda.feel:feel-engine` with the classifier
`scala-shaded`, version 1.19.3. Operaton 2.2 brings the same artifact without a classifier,
version 1.22.1, and Scala next to it. For Maven these are two artifacts, so an application with
both adapters has both jars on its classpath. Both hold the classes of `org.camunda.feel`, in
different versions, and the JVM loads whichever comes first. Measured on 2026-10-28 in
`migration-tests`: with Operaton's jar first, the Camunda 7 engine does not start
(`NoSuchMethodError` in `ValueMapper$CompositeValueMapper`). With Camunda's jar first, Operaton's
FEEL integration, which is compiled against the plain Scala types, cannot work either.

So the newer library stays and serves Operaton, because Camunda 7.24 gets no more releases. The
Camunda 7 engine loses its copy and evaluates decisions with its older FEEL implementation
(`dmnFeelEnableLegacyBehavior`), which needs no Scala. The module `migration-tests` does exactly
that, in its `pom.xml` and in `MigrationTestApplication`, and `OperatonSideBySideWithCamunda7IT`
runs a decision on each engine. The README shows an application the same two steps.

The cost is that a decision of the Camunda 7 engine is limited to what the older implementation
understands while both engines run. An application only meets this in the side-by-side phase of
a migration, and only with decisions. An application on one engine is not affected.

This holds for Operaton 2.2. Operaton 2.1 still used the shaded jar, so with it Maven picked one
version for both engines. Each new Operaton minor runs `migration-tests` again, and a change of
the FEEL engine there changes this entry.

### 9. The Camunda 7 adapter comes from `vanillabp/camunda7-adapter` as `io.vanillabp`

Proposed by story 2085. It changes decisions 5 and 7.

**Why.** Since story 2083 the Camunda 7 adapter is built in `vanillabp/camunda7-adapter` again. Its
artifacts have the groupId `io.vanillabp`, and its snapshots go to the snapshot repository of Maven
Central. The copy in the Camunda Community Hub is frozen and holds only version 1.

**What holds.**

- `bin/sync-from-camunda7.sh` clones `vanillabp/camunda7-adapter` by default.
- The recipe `rewrite/vanillabp-operaton.yml` no longer renames a groupId, because both adapters use
  `io.vanillabp`. The rule `camunda7` -> `operaton` renames the artifactIds `io.vanillabp:camunda7-*`
  and the links to the repository and its wiki. No artifact of `spi-for-java` or of the platform has
  `camunda7` in its name, so the rule leaves them alone. A mention of version 1 in the Camunda
  Community Hub stays as it is.
- Decision 5: the groupId row of the table reads `io.vanillabp` on both sides. The artifactIds keep
  the two adapters apart.
- Decision 7: `migration-tests` takes the Camunda 7 adapter from the snapshot repository of Maven
  Central, like every other VanillaBP snapshot. There is no GitHub Packages registry, no server entry
  and no token any more. The module stays a module of its own for the first reason of decision 7.

**Addendum:** see decision 10.

### 10. Snapshots go to GitHub Packages as well, and the workflows can read them from there

Proposed by story 2158. It adds to decision 9, which says that `migration-tests` takes the Camunda 7 adapter from the snapshot repository of Maven Central and needs no GitHub Packages registry, no server entry and no token. That stays true on your machine. In the workflows the Camunda 7 adapter comes from GitHub Packages once the switch below is set, with the `GITHUB_TOKEN` of the run.

**What was decided.** Every push to `main` publishes the snapshots of this repository to two places in one build: to
the snapshot repository of Maven Central, as before, and to GitHub Packages of this repository.
The profile `publish-snapshots` of the root POM does both. It publishes only where the Actions
variable `VANILLABP_PUBLISH` is `true`; the vanillabp organization sets it, a fork does not have it.
The profile `central-portal` of the parent is still what a release uses.

The workflows read the snapshots of the other VanillaBP repositories from GitHub Packages once the
organization sets the Actions variable `VANILLABP_SNAPSHOTS_FROM` to `github-packages`, and from the
snapshot repository of Maven Central while it is not set. `.github/maven/settings.xml` holds the
switch, a filter per repository by groupId, and the servers. Reading uses the `GITHUB_TOKEN` of
the run with `packages: read`; nobody has to create a token.

On your own machine nothing changes. `io.vanillabp:release-parent` still names the snapshot
repository of Maven Central, it still gets every snapshot, and reading it still needs no login.

**Why.** Maven Central answers the shared GitHub runners with HTTP 429 and a pause of about half an hour.
A build which asks Central for every VanillaBP snapshot stops there several times a week. GitHub
Packages has no such limit documented, and downloads with the `GITHUB_TOKEN` cost nothing. Maven
Central keeps the snapshots for everybody without a GitHub token. The switch is an organization
variable, so the builds go back to Maven Central without a push should GitHub Packages fail.

**Where it is referred to:** the addendum line of decision 9. `CONTRIBUTING.md` describes the
same, and `.github/maven/settings.xml` and the workflows say it in their comments.

**Addendum:** see decision 11.

### 11. Addendum to decision 10: a snapshot reaches Maven Central again, without a sources jar, and a refused request is repeated

Proposed by stories 2162 and 2160, 2026-11-13. It adds to decision 10, which brought the profile
`publish-snapshots`. The rest of decision 10 holds. `adapter-platform-integration` made the same
change, see decision 139 in adapter-platform-integration for the URL and decision 140 in
adapter-platform-integration for the sources jar and the repeats.

Here only `migration-tests/pom.xml` changes, see below. `pom.xml` and `.mvn/maven.config` come from
the Camunda 7 adapter through `bin/sync-from-camunda7.sh`, which keeps only the files in
`KEPT_BY_MAIN` as they are here. So this repository takes the change with the first sync after it is
on `main` of the Camunda 7 adapter, see decision 66 in camunda7-adapter. Until then a snapshot of
this repository still goes to GitHub Packages twice and not to Maven Central. The paragraphs below
describe the POM as it is after that sync.

**The snapshot reaches Maven Central again.** The profile `publish-snapshots` sets the snapshot
repository of the POM to GitHub Packages, because that is where `maven-deploy-plugin` uploads.
`central-publishing-maven-plugin` 0.12.0 reads the same setting: where its configuration names no
`centralSnapshotsUrl`, it uploads a snapshot to the snapshot repository of the POM. So since story
2158 every snapshot of this repository went to GitHub Packages twice and never to Maven Central, and
the build stayed green. The configuration of the plugin in the profile now names
`https://central.sonatype.com/repository/maven-snapshots/` as `centralSnapshotsUrl`. The modules
which are not published keep `skip.central.release`, so the list of published modules is the same as
before.

**A snapshot carries no sources jar.** Maven Central answers the GitHub runners with 429 Too Many
Requests, and every file a publish uploads is one more request. The root POM no longer binds
`maven-source-plugin`, so a snapshot carries the jar, the POM and their checksums, and nothing else.
GitHub Packages gets no sources jar either, because both repositories get their files from one
build. A release still carries a sources jar and a javadoc jar: the release workflow runs with the
profile `release` of `io.vanillabp:release-parent`, which binds both. A developer who opens a class
of a snapshot in the IDE sees decompiled code again, as before the root POM bound the plugin. In
this repository `migration-tests` builds no jar and publishes nothing, so it binds the plugin
itself. Without a file of its own in a module, `central-publishing-maven-plugin` stops the whole
publish, also for a module it leaves out. Its POM belongs to this repository, so the sync does not
bring this, and it is changed here.

**A refused request is repeated.** `.mvn/maven.config` tells Maven to repeat a request which a
server answers with 429 or 503. It repeats a request at most twice and waits as long as the
`Retry-After` header asks, up to 35 minutes. Central blocks a runner for about half an hour, and
every request during the block makes it longer, so waiting once is better than asking again. Maven
also uploads one file after the other instead of five at once. The file applies to every build of
this repository, on a laptop and in a pull request as well.

**Where it is referred to:** the addendum line of decision 10 and the comment at
`maven-source-plugin` in `migration-tests/pom.xml`. In the Camunda 7 adapter, the comments in
`pom.xml` and in `.mvn/maven.config` describe it.
