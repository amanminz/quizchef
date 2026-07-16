package io.quizchef.session;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Enforces the session module's architectural boundaries at build time.
 */
@AnalyzeClasses(packages = "io.quizchef.session", importOptions = ImportOption.DoNotIncludeTests.class)
class SessionArchitectureTest {

    @ArchTest
    static final ArchRule domainIsFrameworkIndependent =
            noClasses().that().resideInAPackage("..session.domain..")
                    .should().dependOnClassesThat().resideInAnyPackage("org.springframework..")
                    .because("the session domain must stay framework independent");

    @ArchTest
    static final ArchRule domainDoesNotDependOnOuterLayers =
            noClasses().that().resideInAPackage("..session.domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..session.api..", "..session.application..", "..session.infrastructure..")
                    .because("dependencies point inward: api -> application -> domain");

    @ArchTest
    static final ArchRule domainTouchesOnlyOtherDomainsAcrossModules =
            noClasses().that().resideInAPackage("..session.domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "io.quizchef.identity.api..",
                            "io.quizchef.identity.application..",
                            "io.quizchef.identity.infrastructure..",
                            "io.quizchef.quiz.api..",
                            "io.quizchef.quiz.application..",
                            "io.quizchef.quiz.infrastructure..")
                    .because("cross-module coupling is limited to identity and quiz domain value objects");
}
