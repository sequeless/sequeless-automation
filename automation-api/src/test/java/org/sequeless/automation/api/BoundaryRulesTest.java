package org.sequeless.automation.api;

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
 * Mechanically enforced boundaries for the automation API. The API depends only on the JDK, the kernel
 * value and identity types, the ontology automation contract, and itself. It never depends on the Store
 * modules, the prototype packages, or the adapters, core, testkit or app modules. Its type names also never
 * collide with the ontology automation contract's, so a name means one thing across both.
 */
@AnalyzeClasses(
        packages = "org.sequeless.automation.api",
        importOptions = {ImportOption.DoNotIncludeTests.class})
class BoundaryRulesTest {

    private static final String AUTOMATION = "org.sequeless.automation";
    private static final String ONTOLOGY_AUTOMATION = "org.sequeless.ontology.automation";

    @ArchTest
    static final ArchRule api_depends_only_on_the_allowed_packages = noClasses()
            .that()
            .resideInAPackage("org.sequeless.automation.api..")
            .should()
            .dependOnClassesThat()
            .resideOutsideOfPackages(
                    "java..",
                    "org.sequeless.kernel..",
                    "org.sequeless.ontology.automation..",
                    "org.sequeless.automation.api..")
            .as("the automation API depends on the JDK, the kernel types, the ontology automation contract and itself");

    @ArchTest
    static final ArchRule api_does_not_depend_on_the_prototype_or_the_store_modules = noClasses()
            .that()
            .resideInAPackage("org.sequeless.automation.api..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.sequeless.spi..",
                    "org.sequeless.core..",
                    "org.sequeless.adapter..",
                    "org.sequeless.testkit..",
                    "org.sequeless.app..")
            .as("the automation API must not depend on the prototype packages or the Store modules");

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
