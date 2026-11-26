package org.sequeless.automation.inprocess;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Mechanically enforced boundaries for the in-process adapter. Its main code depends on the JDK, the kernel
 * types, the ontology automation contract, the schema contract that contract exposes, the automation API, the
 * SLF4J logging facade, and itself. It never depends on the prototype packages, the automation core, the sibling adapters, the
 * autoconfiguration, or Spring. Its type names never collide with the ontology automation contract's.
 *
 * <p>The rules run on main code only, as the API's rules do. The tests may use the testkit, JUnit, AssertJ and
 * the JDK HTTP server.
 */
@AnalyzeClasses(
        packages = "org.sequeless.automation.inprocess",
        importOptions = {ImportOption.DoNotIncludeTests.class})
class BoundaryRulesTest {

    private static final String AUTOMATION = "org.sequeless.automation";
    private static final String ONTOLOGY_AUTOMATION = "org.sequeless.ontology.automation";

    @ArchTest
    static final ArchRule adapter_depends_only_on_the_allowed_packages = noClasses()
            .that()
            .resideInAPackage("org.sequeless.automation.inprocess..")
            .should()
            .dependOnClassesThat()
            .resideOutsideOfPackages(
                    "java..",
                    "org.sequeless.kernel..",
                    "org.sequeless.ontology.automation..",
                    "org.sequeless.ontology.schema..",
                    "org.sequeless.automation.api..",
                    "org.sequeless.automation.inprocess..",
                    "org.slf4j..")
            .as("the in-process adapter depends on the JDK, the kernel types, the ontology automation contract,"
                    + " the schema contract it exposes, the automation API, the SLF4J logging facade and itself");

    @ArchTest
    static final ArchRule adapter_does_not_depend_on_the_prototype_core_siblings_or_spring = noClasses()
            .that()
            .resideInAPackage("org.sequeless.automation.inprocess..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.sequeless.spi..",
                    "org.sequeless.core..",
                    "org.sequeless.adapter..",
                    "org.sequeless.app..",
                    "org.sequeless.automation.core..",
                    "org.sequeless.automation.temporal..",
                    "org.sequeless.automation.autoconfigure..",
                    "org.sequeless.automation.testkit..",
                    "org.springframework..")
            .as("the in-process adapter must not depend on the prototype packages, the automation core, the sibling"
                    + " adapters, the autoconfiguration or Spring");

    @Test
    void noAutomationTypeSharesASimpleNameWithAnOntologyAutomationType() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(AUTOMATION, ONTOLOGY_AUTOMATION);

        Set<String> automationNames = simpleNamesUnder(classes, AUTOMATION);
        Set<String> ontologyNames = simpleNamesUnder(classes, ONTOLOGY_AUTOMATION);
        assertThat(automationNames).contains("StoreEventRow");
        assertThat(ontologyNames).contains("Action", "WebhookAction");

        Set<String> shared = new TreeSet<>(automationNames);
        shared.retainAll(ontologyNames);
        assertThat(shared)
                .as("simple names shared by an automation type and an ontology automation type")
                .isEmpty();
    }

    /** The simple names of the classes in {@code packageRoot} or below, skipping {@code package-info}. */
    private static Set<String> simpleNamesUnder(JavaClasses classes, String packageRoot) {
        Set<String> names = new TreeSet<>();
        for (JavaClass javaClass : classes) {
            String packageName = javaClass.getPackageName();
            String simpleName = javaClass.getSimpleName();
            boolean underRoot = packageName.equals(packageRoot) || packageName.startsWith(packageRoot + ".");
            if (underRoot && !simpleName.isEmpty() && !simpleName.equals("package-info")) {
                names.add(simpleName);
            }
        }
        return names;
    }
}
