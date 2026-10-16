package com.a207.runkk;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

/** 코드 컨벤션 2장(백엔드)의 패키지·계층 규칙. 규칙이 빈 집합에 걸리는 동안은 통과한다 (src/test/resources/archunit.properties). */
class ArchitectureTest {

    private static final String ROOT = "com.a207.runkk";
    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes =
                new ClassFileImporter()
                        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages(ROOT);
    }

    @Test
    void layers_controller_service_repository() {
        layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .withOptionalLayers(true)
                .layer("Controller")
                .definedBy("..controller..")
                .layer("Service")
                .definedBy("..service..")
                .layer("Repository")
                .definedBy("..repository..")
                .whereLayer("Controller")
                .mayNotBeAccessedByAnyLayer()
                .whereLayer("Service")
                .mayOnlyBeAccessedByLayers("Controller", "Service")
                .whereLayer("Repository")
                .mayOnlyBeAccessedByLayers("Service")
                .check(classes);
    }

    @Test
    void controllers_doNotTouchEntitiesOrRepositories() {
        noClasses()
                .that()
                .resideInAPackage("..controller..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..entity..", "..repository..")
                .check(classes);
    }

    @Test
    void domains_haveNoCycles() {
        slices().matching(ROOT + ".(*)..").should().beFreeOfCycles().check(classes);
    }

    @Test
    void global_doesNotDependOnDomains() {
        noClasses()
                .that()
                .resideInAPackage("..global..")
                .should()
                .dependOnClassesThat(
                        resideInAPackage(ROOT + "..")
                                .and(not(resideInAnyPackage("..global..", ROOT))))
                .check(classes);
    }

    @Test
    void controllers_areNamedAndAnnotated() {
        classes()
                .that()
                .resideInAPackage("..controller..")
                .and()
                .areAnnotatedWith(RestController.class)
                .should()
                .haveSimpleNameEndingWith("Controller")
                .check(classes);
    }

    @Test
    void services_areNamedAndTransactional() {
        classes()
                .that()
                .resideInAPackage("..service..")
                .and()
                .areAnnotatedWith(Service.class)
                .should()
                .haveSimpleNameEndingWith("Service")
                .andShould()
                .beAnnotatedWith(Transactional.class)
                .check(classes);
    }

    @Test
    void repositories_areNamed() {
        classes()
                .that()
                .resideInAPackage("..repository..")
                .and()
                .areInterfaces()
                .should()
                .haveSimpleNameEndingWith("Repository")
                .check(classes);
    }

    @Test
    void dtos_areRecords() {
        classes().that().resideInAPackage("..dto..").should().beRecords().check(classes);
    }

    @Test
    void entities_haveNoSetters() {
        noMethods()
                .that()
                .areDeclaredInClassesThat()
                .resideInAPackage("..entity..")
                .should()
                .haveNameStartingWith("set")
                .check(classes);
    }

    @Test
    void generalCodingRules() {
        NO_CLASSES_SHOULD_USE_FIELD_INJECTION.check(classes);
        NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS.check(classes);
        NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING.check(classes);
    }
}
