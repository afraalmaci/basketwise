package io.github.afraalmaci.basketwise;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Keeps the module boundaries from ADR 0001 true as the code grows. These rules run in
 * CI with the other tests, so a forbidden import fails the build instead of a code review.
 */
@AnalyzeClasses(packages = "io.github.afraalmaci.basketwise", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /** The engine knows nothing about Spring, databases, HTTP or maps: only the JDK. */
    @ArchTest
    static final ArchRule optimizationIsPlainJava = classes()
            .that().resideInAPackage("..optimization..")
            .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "..optimization..")
            .because("the optimizer must be testable in milliseconds without Spring or a database (ADR 0001)");

    /** Routing algorithms stay plain Java too; only the adapters at its edge may use Spring. */
    @ArchTest
    static final ArchRule routingCoreIsPlainJava = noClasses()
            .that().resideInAPackage("..routing..")
            .and().haveSimpleNameNotEndingWith("Controller")
            .and().haveSimpleNameNotEndingWith("Config")
            .and().doNotHaveSimpleName("OpenRouteServiceClient")
            .should().dependOnClassesThat().resideInAPackage("org.springframework..")
            .because("tour planning and travel time logic are tested without Spring");

    /** Feature packages may depend on each other, but never in a circle. */
    @ArchTest
    static final ArchRule noCycles = slices()
            .matching("io.github.afraalmaci.basketwise.(*)..")
            .should().beFreeOfCycles();
}
