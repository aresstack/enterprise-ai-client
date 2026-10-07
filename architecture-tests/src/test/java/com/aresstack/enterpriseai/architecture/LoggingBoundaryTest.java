package com.aresstack.enterpriseai.architecture;

import com.aresstack.enterpriseai.acp.demo.archfixture.logging.DemoAgentWritingStderr;
import com.aresstack.enterpriseai.application.archfixture.logging.UseCaseWithLogger;
import com.aresstack.enterpriseai.chat.openai.archfixture.logging.AdapterPrintingStackTrace;
import com.aresstack.enterpriseai.chat.openai.archfixture.logging.AdapterUsingSlf4j;
import com.aresstack.enterpriseai.domain.archfixture.logging.ValuePrintingToStdout;
import com.aresstack.enterpriseai.security.keepassrpc.archfixture.logging.AdapterWithJulLogger;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Auftrag: keine Secrets in Logs. Der Kern loggt gar nicht, Frameworks fehlen, Konsole nur im Demo-Agenten. */
public class LoggingBoundaryTest {

    private static final ModuleRegistry REGISTRY = ModuleRegistry.standard();

    @Test
    public void productionCodeKeepsLoggingOutOfTheCore() {
        Violations.assertNone("Logging-Grenze verletzt", Violations.of(LoggingRules.all(REGISTRY), ProductionClasses.all()));
    }

    @Test
    public void loggerInUseCaseIsDetected() {
        List<String> violations = Violations.of(Collections.singletonList(LoggingRules.coreAndUiLibraryDoNotLog(REGISTRY)),
                ProductionClasses.of(UseCaseWithLogger.class));
        assertEquals(violations.toString(), 1, violations.size());
        assertTrue(violations.get(0), violations.get(0).contains("UseCaseWithLogger"));
    }

    @Test
    public void loggingFrameworkInAdapterIsDetected() {
        List<String> violations = Violations.of(Collections.singletonList(LoggingRules.noLoggingFrameworksInProduction()),
                ProductionClasses.of(AdapterUsingSlf4j.class));
        assertEquals(violations.toString(), 1, violations.size());
        assertTrue(violations.get(0), violations.get(0).contains("AdapterUsingSlf4j"));
    }

    @Test
    public void consoleOutputAndStackTracesAreDetected() {
        List<String> violations = Violations.of(Collections.singletonList(LoggingRules.noConsoleOutputOutsideTheDemoAgent(REGISTRY)),
                ProductionClasses.of(ValuePrintingToStdout.class, AdapterPrintingStackTrace.class));
        assertEquals(violations.toString(), 1, violations.size());
        assertTrue(violations.get(0), violations.get(0).contains("System.out"));
        assertTrue(violations.get(0), violations.get(0).contains("System.err"));
        assertTrue(violations.get(0), violations.get(0).contains("printStackTrace"));
    }

    @Test
    public void julInAdapterAndStderrInDemoAgentPass() {
        Violations.assertNone("Erlaubtes Logging darf nicht anschlagen",
                Violations.of(LoggingRules.all(REGISTRY), ProductionClasses.of(AdapterWithJulLogger.class, DemoAgentWritingStderr.class)));
    }
}
