package io.quizchef.identity;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Enforces the identity module's architectural boundaries at build time.
 */
@AnalyzeClasses(packages = "io.quizchef.identity", importOptions = ImportOption.DoNotIncludeTests.class)
class IdentityArchitectureTest {

    @ArchTest
    static final ArchRule domainIsFrameworkIndependent =
            noClasses().that().resideInAPackage("..identity.domain..")
                    .should().dependOnClassesThat().resideInAnyPackage("org.springframework..")
                    .because("the identity domain must stay framework independent");

    @ArchTest
    static final ArchRule businessCodeDoesNotDependOnSpringSecurity =
            noClasses().that().resideOutsideOfPackage("..identity.infrastructure..")
                    .should().dependOnClassesThat().resideInAnyPackage("org.springframework.security..")
                    .because("only infrastructure adapters may touch Spring Security");

    @ArchTest
    static final ArchRule domainDoesNotDependOnOuterLayers =
            noClasses().that().resideInAPackage("..identity.domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..identity.api..", "..identity.application..", "..identity.infrastructure..")
                    .because("dependencies point inward: api -> application -> domain");
}
