package org.sequeless.automation.testkit;

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
 * Mechanically enforced boundaries for the automation testkit. The testkit depends on the JDK, the kernel
 * types, the ontology automation contract, the automation API, JUnit and AssertJ, and on itself. It never
 * depends on the automation core, the adapters, the Store modules, the prototype packages or Spring. Its
 * type names also never collide with the ontology automation contract's, so a name means one thing across
 * both.
 */
@AnalyzeClasses(
        packages = "org.sequeless.automation.testkit",
        importOptions = {ImportOption.DoNotIncludeTests.class})
class BoundaryRulesTest {

    private static final String AUTOMATION = "org.sequeless.automation";
    private static final String ONTOLOGY_AUTOMATION = "org.sequeless.ontology.automation";

    @ArchTest
    static final ArchRule testkit_depends_only_on_the_allowed_packages = noClasses()
            .that()
            .resideInAPackage("org.sequeless.automation.testkit..")
            .should()
            .dependOnClassesThat()
            .resideOutsideOfPackages(
                    "java..",
                    "org.sequeless.kernel..",
                    "org.sequeless.ontology.automation..",
                    "org.sequeless.automation.api..",
                    "org.junit..",
                    "org.assertj..",
                    "org.sequeless.automation.testkit..")
            .as("the automation testkit depends on the JDK, the kernel types, the ontology automation contract, "
                    + "the automation API, JUnit, AssertJ and itself");

    @ArchTest
    static final ArchRule testkit_does_not_depend_on_the_prototype_or_the_store_or_spring = noClasses()
            .that()
            .resideInAPackage("org.sequeless.automation.testkit..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.sequeless.spi..",
                    "org.sequeless.core..",
                    "org.sequeless.adapter..",
                    "org.sequeless.app..",
                    "org.springframework..")
            .as("the automation testkit must not depend on the prototype packages, the Store modules or Spring");

    @Test
    void noTestkitTypeSharesASimpleNameWithAnOntologyAutomationType() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(AUTOMATION, ONTOLOGY_AUTOMATION);

        Set<String> testkitNames = simpleNamesUnder(classes, "org.sequeless.automation.testkit");
        Set<String> ontologyNames = simpleNamesUnder(classes, ONTOLOGY_AUTOMATION);
        assertThat(testkitNames).contains("AutomationContract", "InMemoryStoreCommandPort");
        assertThat(ontologyNames).contains("Action", "WebhookAction");

        Set<String> shared = new TreeSet<>(testkitNames);
        shared.retainAll(ontologyNames);
        assertThat(shared)
                .as("simple names shared by a testkit type and an ontology automation type")
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
