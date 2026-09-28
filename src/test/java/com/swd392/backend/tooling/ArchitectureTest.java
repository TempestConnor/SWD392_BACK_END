package com.swd392.backend.tooling;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class ArchitectureTest {

    @Test
    void controllers_do_not_access_persistence_ok() {
        // given
        var classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.swd392.backend");
        // when
        var rule = noClasses()
                .that()
                .resideInAPackage("..controller..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..repository..", "..entity..", "jakarta.persistence..");
        // then
        rule.check(classes);
    }

    @Test
    void models_remain_independent_of_frameworks_and_dtos_ok() {
        // given
        var classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.swd392.backend");
        // when
        var rule = noClasses()
                .that()
                .resideInAPackage("..model..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("jakarta.persistence..", "org.springframework..", "..dto..")
                .allowEmptyShould(true);
        // then
        rule.check(classes);
    }

    @Test
    void services_do_not_depend_on_controllers_ok() {
        // given
        var classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.swd392.backend");
        // when
        var rule = noClasses()
                .that()
                .resideInAPackage("..service..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..controller..")
                .allowEmptyShould(true);
        // then
        rule.check(classes);
    }

    @Test
    void mappers_do_not_depend_on_services_or_repositories_ok() {
        // given
        var classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.swd392.backend");
        // when
        var rule = noClasses()
                .that()
                .resideInAPackage("..mapper..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..service..", "..repository..")
                .allowEmptyShould(true);
        // then
        rule.check(classes);
    }
}
