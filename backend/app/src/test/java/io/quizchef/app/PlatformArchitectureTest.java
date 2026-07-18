package io.quizchef.app;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Observability is orthogonal to business logic (Phase 3 PR #2 / RFC-010):
 * no business module may depend on {@code io.quizchef.platform}, so no
 * feature can ever read a metric or branch on logging. Checked here, not
 * per-module, because {@code app} is the only place every business package
 * coexists with {@code platform} on one classpath.
 */
@AnalyzeClasses(packages = "io.quizchef", importOptions = ImportOption.DoNotIncludeTests.class)
class PlatformArchitectureTest {

    @ArchTest
    static final ArchRule businessCodeDoesNotDependOnPlatform =
            noClasses().that().resideOutsideOfPackage("..platform..")
                    .and().resideOutsideOfPackage("..app..")
                    .should().dependOnClassesThat().resideInAPackage("io.quizchef.platform..")
                    .because("observability owns nothing — no feature may read a metric or branch on logging");
}
