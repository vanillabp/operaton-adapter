/**
 * What this adapter knows about its engine. It hands these facts out to an extension which
 * runs inside the same engine.
 * <p>
 * Operaton is embedded. An extension of this adapter runs in the same JVM. It works on the
 * same execution tree and against the same process definitions. So every fact it needs is a
 * fact the adapter already looked up. Where the adapter kept that lookup to itself, an
 * extension had to read the engine's internals a second time. Two readings of the same
 * internals drift apart on the next engine release.
 * <p>
 * So this package is API, not internals. Each type says what it promises and what it does
 * not promise on purpose. It is Operaton API of this repository, like
 * {@link io.vanillabp.operaton.engine.OperatonEngineCustomizer}. It is not part of the
 * VanillaBP adapter SPI, because nothing here is a mechanism another BPMS shares.
 * <p>
 * Decision 21 in the Camunda 7 adapter's DECISIONS.md says why an extension uses the adapter's own
 * API and not the engine.
 */
package io.vanillabp.operaton.api;
