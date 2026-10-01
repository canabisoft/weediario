package br.com.weediario;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Garante a regra de dependência da Clean Architecture e o isolamento entre bounded contexts.
 */
class ArchitectureTest {

    private static final String ROOT = "br.com.weediario";
    private static final List<String> CONTEXTS = List.of("identity", "diary", "media", "review", "social", "catalog", "treatment", "notification", "nutrition");

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);

    @Test
    void domainIsFrameworkFree() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "jakarta..", "org.hibernate..",
                        "..application..", "..infrastructure..", "..presentation..")
                .check(CLASSES);
    }

    @Test
    void applicationIsFrameworkFree() {
        noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "jakarta..", "org.hibernate..",
                        "..infrastructure..", "..presentation..")
                .check(CLASSES);
    }

    /**
     * Um contexto só conversa com outro através de um adapter na própria camada de infraestrutura
     * (ex.: {@code diary.infrastructure.media.MediaPhotoLibrary}).
     */
    @ParameterizedTest
    @ValueSource(strings = {"identity", "diary", "media", "review", "social", "catalog", "treatment", "notification", "nutrition"})
    void contextsOnlyTalkThroughInfrastructureAdapters(String context) {
        String[] otherContexts = CONTEXTS.stream()
                .filter(other -> !other.equals(context))
                .map(other -> ROOT + "." + other + "..")
                .toArray(String[]::new);

        noClasses()
                .that().resideInAnyPackage(
                        ROOT + "." + context + ".domain..",
                        ROOT + "." + context + ".application..",
                        ROOT + "." + context + ".presentation..")
                .should().dependOnClassesThat().resideInAnyPackage(otherContexts)
                .allowEmptyShould(true)
                .check(CLASSES);
    }
}
