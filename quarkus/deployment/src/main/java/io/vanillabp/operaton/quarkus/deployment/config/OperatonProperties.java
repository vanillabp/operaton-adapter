package io.vanillabp.operaton.quarkus.deployment.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

/**
 * Operaton adapter build-time properties. It is empty, because the adapter's engine
 * settings are RUN_TIME configuration (see the runtime module's
 * {@code VanillaBpOperatonProperties} overlay).
 */
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
@ConfigMapping(prefix = "vanillabp")
public interface OperatonProperties {

}
