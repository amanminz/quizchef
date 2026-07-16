package io.quizchef.websocket;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Guards the transport-independence boundary (ADR-004) from the transport
 * side: no domain — session's or anyone else's on this module's classpath —
 * may depend on the websocket module. Realtime is downstream of the domain,
 * never the reverse.
 */
@AnalyzeClasses(packages = "io.quizchef", importOptions = ImportOption.DoNotIncludeTests.class)
class WebSocketArchitectureTest {

    @ArchTest
    static final ArchRule domainNeverDependsOnTransport =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAPackage("io.quizchef.websocket..")
                    .because("realtime transport is downstream of the domain (ADR-004); "
                            + "the domain must never know a transport exists");

    @ArchTest
    static final ArchRule sessionModuleNeverDependsOnTransport =
            noClasses().that().resideInAPackage("io.quizchef.session..")
                    .should().dependOnClassesThat().resideInAPackage("io.quizchef.websocket..")
                    .because("the session engine expresses realtime as domain events, "
                            + "never by calling the transport (ADR-004/005)");
}
