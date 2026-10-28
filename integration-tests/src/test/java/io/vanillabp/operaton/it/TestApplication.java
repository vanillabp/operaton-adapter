package io.vanillabp.operaton.it;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The test application. It boots a real embedded Operaton engine on H2 together with the
 * VanillaBP Operaton adapter. The engine shares H2 with the JPA aggregate persistence. Used by
 * {@link OperatonStartWorkflowIT}.
 */
@SpringBootApplication
public class TestApplication {

}
