package org.sequeless.automation.temporal;

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
 * Mechanically enforced boundaries for the Temporal adapter. Its main code depends on the JDK, the kernel
 * types, the automation API and the ontology automation and schema contracts, on the Temporal SDK, and, in
 * one class only, on Spring Boot's configuration binding. It never depends on the automation core, the Store,
 * the prototype packages, the other adapters or the autoconfiguration module. The adapter also holds no
 * autoconfiguration class: that lives in {@code automation-spring-boot-autoconfig}.
 *
 * <p>The ontology schema contract is allowed beyond the brief's list: {@code Automation} exposes its
 * {@code OntologyVersion}, which the adapter reads to check the stamp on each row.
 */
@AnalyzeClasses(
        packages = "org.sequeless.automation.temporal",
        importOptions = {ImportOption.DoNotIncludeTests.class})
class BoundaryRulesTest {

    private static final String TEMPORAL = "org.sequeless.automation.temporal";
    private static final String ONTOLOGY_AUTOMATION = "org.sequeless.ontology.automation";

    @ArchTest
    static final ArchRule adapter_depends_only_on_the_allowed_packages = noClasses()
            .that()
            .resideInAPackage("org.sequeless.automation.temporal..")
            .should()
            .dependOnClassesThat()
            .resideOutsideOfPackages(
                    "java..",
                    "org.sequeless.kernel..",
                    "org.sequeless.ontology.automation..",
                    "org.sequeless.ontology.schema..",
                    "org.sequeless.automation.api..",
                    "org.sequeless.automation.temporal..",
                    "io.temporal..",
                    "org.springframework..",
                    "org.slf4j..")
            .as("the Temporal adapter depends on the JDK, the kernel, the automation API and contracts, and the SDK");

    @ArchTest
    static final ArchRule adapter_does_not_depend_on_core_store_prototype_or_other_adapters = noClasses()
            .that()
            .resideInAPackage("org.sequeless.automation.temporal..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.sequeless.automation.core..",
                    "org.sequeless.automation.inprocess..",
                    "org.sequeless.automation.autoconfigure..",
                    "org.sequeless.spi..",
                    "org.sequeless.core..",
                    "org.sequeless.adapter..",
                    "org.sequeless.app..",
                    "org.sequeless.testkit..")
            .as(
                    "the Temporal adapter must not depend on the automation core, the Store, the prototype or other adapters");

    @ArchTest
    static final ArchRule only_the_properties_class_depends_on_spring = noClasses()
            .that()
            .resideInAPackage("org.sequeless.automation.temporal..")
            .and()
            .doNotHaveSimpleName("TemporalAutomationProperties")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("org.springframework..")
            .as("only TemporalAutomationProperties binds Spring Boot configuration");

    @ArchTest
    static final ArchRule the_adapter_holds_no_autoconfiguration_class = noClasses()
            .that()
            .resideInAPackage("org.sequeless.automation.temporal..")
            .should()
            .haveSimpleNameEndingWith("AutoConfiguration")
            .as("the Spring autoconfiguration lives in automation-spring-boot-autoconfig");

    @Test
    void noTemporalTypeSharesASimpleNameWithAnOntologyAutomationType() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(TEMPORAL, ONTOLOGY_AUTOMATION);

        Set<String> temporalNames = simpleNamesUnder(classes, TEMPORAL);
        Set<String> ontologyNames = simpleNamesUnder(classes, ONTOLOGY_AUTOMATION);
        assertThat(temporalNames).contains("TemporalAutomationPort");
        assertThat(ontologyNames).contains("Action", "WebhookAction");

        Set<String> shared = new TreeSet<>(temporalNames);
        shared.retainAll(ontologyNames);
        assertThat(shared)
                .as("simple names shared by a Temporal type and an ontology automation type")
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
