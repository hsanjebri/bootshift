package dev.bootshift;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Module boundaries from README section 5: package by feature, no cycles, web on the outside. */
@AnalyzeClasses(packages = "dev.bootshift", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule featurePackagesHaveNoCycles =
            slices().matching("dev.bootshift.(*)..").should().beFreeOfCycles();

    @ArchTest
    static final ArchRule onlyWebDependsOnWeb =
            noClasses().that().resideOutsideOfPackage("dev.bootshift.web..")
                    .should().dependOnClassesThat().resideInAPackage("dev.bootshift.web..");

    @ArchTest
    static final ArchRule repoDoesNotKnowAboutRuns =
            noClasses().that().resideInAPackage("dev.bootshift.repo..")
                    .should().dependOnClassesThat().resideInAPackage("dev.bootshift.run..");

    @ArchTest
    static final ArchRule noJackson2 =
            noClasses().should().dependOnClassesThat().resideInAPackage("com.fasterxml.jackson.databind..");
}
