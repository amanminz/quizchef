package io.quizchef.quiz;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Enforces the quiz module's architectural boundaries at build time.
 */
@AnalyzeClasses(packages = "io.quizchef.quiz", importOptions = ImportOption.DoNotIncludeTests.class)
class QuizArchitectureTest {

    @ArchTest
    static final ArchRule domainIsFrameworkIndependent =
            noClasses().that().resideInAPackage("..quiz.domain..")
                    .should().dependOnClassesThat().resideInAnyPackage("org.springframework..")
                    .because("the quiz domain must stay framework independent");

    @ArchTest
    static final ArchRule domainDoesNotDependOnOuterLayers =
            noClasses().that().resideInAPackage("..quiz.domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..quiz.api..", "..quiz.application..", "..quiz.infrastructure..")
                    .because("dependencies point inward: api -> application -> domain");

    @ArchTest
    static final ArchRule domainTouchesOnlyIdentityDomainAcrossModules =
            noClasses().that().resideInAPackage("..quiz.domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "io.quizchef.identity.api..",
                            "io.quizchef.identity.application..",
                            "io.quizchef.identity.infrastructure..")
                    .because("cross-module coupling is limited to identity domain value objects");
}
