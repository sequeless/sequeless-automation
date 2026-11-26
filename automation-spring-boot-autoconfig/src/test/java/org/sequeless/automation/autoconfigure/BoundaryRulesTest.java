package org.sequeless.automation.autoconfigure;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Mechanically enforced boundaries for the autoconfiguration module. Its main code is the Spring wiring. It may depend
 * on the automation modules, the kernel, the expression and ontology contracts, the Temporal SDK, Spring and SLF4J. It
 * never depends on the prototype packages, and never on the testkit. The name rule keeps automation type names apart
 * from the ontology automation contract's.
 */
@AnalyzeClasses(
        packages = "org.sequeless.automation.autoconfigure",
        importOptions = {ImportOption.DoNotIncludeTests.class})
class BoundaryRulesTest {

    private static final String AUTOCONFIGURE = "org.sequeless.automation.autoconfigure";
    private static final String AUTOMATION = "org.sequeless.automation";
    private static final String ONTOLOGY_AUTOMATION = "org.sequeless.ontology.automation";

    @ArchTest
    static final ArchRule autoconfigure_depends_only_on_the_allowed_packages = noClasses()
            .that()
            .resideInAPackage("org.sequeless.automation.autoconfigure..")
            .should()
            .dependOnClassesThat()
            .resideOutsideOfPackages(
                    "java..",
                    "org.sequeless.automation..",
                    "org.sequeless.kernel..",
                    "org.sequeless.ontology..",
                    "org.sequeless.expression.api..",
                    "io.temporal..",
                    "org.springframework..",
                    "org.slf4j..")
            .as("the autoconfiguration depends on the JDK, the automation modules, the kernel, the contracts, the SDK, "
                    + "Spring and SLF4J");

    @ArchTest
    static final ArchRule autoconfigure_does_not_depend_on_the_prototype_packages = noClasses()
            .that()
            .resideInAPackage("org.sequeless.automation.autoconfigure..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.sequeless.spi..", "org.sequeless.core..", "org.sequeless.adapter..", "org.sequeless.app..")
            .as("the autoconfiguration must not depend on the prototype packages");

    @ArchTest
    static final ArchRule autoconfigure_does_not_depend_on_the_testkit = noClasses()
            .that()
            .resideInAPackage("org.sequeless.automation.autoconfigure..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("org.sequeless.automation.testkit..")
            .as("the autoconfiguration's main code must not depend on the testkit, which is a test-scope dependency");

    @Test
    void noAutomationTypeSharesASimpleNameWithAnOntologyAutomationType() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(AUTOMATION, ONTOLOGY_AUTOMATION);

        Set<String> automationNames = simpleNamesUnder(classes, AUTOMATION);
        Set<String> ontologyNames = simpleNamesUnder(classes, ONTOLOGY_AUTOMATION);
        assertThat(automationNames).contains("AutomationCoreAutoConfiguration");
        assertThat(ontologyNames).contains("Action", "WebhookAction");

        Set<String> shared = new TreeSet<>(automationNames);
        shared.retainAll(ontologyNames);
        assertThat(shared)
                .as("simple names shared by an automation type and an ontology automation type")
                .isEmpty();
    }

    @Test
    void theImportsFileListsExactlyTheAutoconfigurationClasses() throws IOException {
        String imports = "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";
        Set<String> listed = new TreeSet<>();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(imports)) {
            assertThat(in).as("the imports file %s", imports).isNotNull();
            new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .filter(line -> !line.isBlank())
                    .forEach(listed::add);
        }

        JavaClasses classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(AUTOCONFIGURE);
        Set<String> declared = new TreeSet<>();
        for (JavaClass javaClass : classes) {
            if (javaClass.getSimpleName().endsWith("AutoConfiguration")) {
                declared.add(javaClass.getFullName());
            }
        }

        assertThat(listed)
                .containsExactlyInAnyOrder(
                        AUTOCONFIGURE + ".AutomationCoreAutoConfiguration",
                        AUTOCONFIGURE + ".InProcessAutomationAutoConfiguration",
                        AUTOCONFIGURE + ".TemporalAutomationAutoConfiguration");
        assertThat(declared).as("every *AutoConfiguration class is listed").isEqualTo(listed);
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
