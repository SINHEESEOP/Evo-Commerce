package com.evo.commerce.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class PackageArchitectureTest {

    private static final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.evo.commerce.domain");

    @Test
    void 도메인_계층은_응용이나_표현_계층에_의존하면_안_된다() {
        noClasses().that().resideInAPackage("..domain")
                .should().dependOnClassesThat().resideInAnyPackage("..application", "..presentation")
                .check(classes);
    }

    @Test
    void 표현_계층은_도메인_계층을_직접_참조할_수_없고_응용_계층을_거쳐야_한다() {
        noClasses().that().resideInAPackage("..presentation")
                .should().dependOnClassesThat().resideInAPackage("..domain")
                .check(classes);
    }
}
