package org.sequeless.automation.core;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Mechanically enforced boundaries for the automation core. The core implements the automation API's ports and
 * depends on the JDK, the kernel types, the expression port, the ontology contracts, the automation API and SLF4J.
 * It never depends on the Store modules, the prototype packages, Spring, or the app.
 */
@AnalyzeClasses(
        packages = "org.sequeless.automation.core",
        importOptions = {ImportOption.DoNotIncludeTests.class})
class BoundaryRulesTest {

    @ArchTest
    static final ArchRule core_depends_only_on_the_allowed_packages = noClasses()
            .that()
            .resideInAPackage("org.sequeless.automation.core..")
            .should()
            .dependOnClassesThat()
            .resideOutsideOfPackages(
                    "java..",
                    "org.sequeless.kernel..",
                    "org.sequeless.ontology..",
                    "org.sequeless.expression.api..",
                    "org.sequeless.automation.api..",
                    "org.sequeless.automation.core..",
                    "org.sequeless.automation.testkit..",
                    "org.junit..",
                    "org.assertj..",
                    "org.slf4j..")
            .as("the automation core depends on the JDK, the kernel types, the ontology contracts, the expression port,"
                    + " the automation API, SLF4J, and (in tests) the testkit and the test libraries");

    @ArchTest
    static final ArchRule core_does_not_depend_on_the_prototype_spring_or_the_app = noClasses()
            .that()
            .resideInAPackage("org.sequeless.automation.core..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.sequeless.spi..",
                    "org.sequeless.core..",
                    "org.sequeless.adapter..",
                    "org.sequeless.app..",
                    "org.springframework..")
            .as("the automation core must not depend on the prototype packages, the adapters, the app or Spring");
}
