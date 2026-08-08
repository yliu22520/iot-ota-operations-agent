package com.yliu22520.iotota;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

public class ArchitectureTest {

    private static JavaClasses importedClasses() {
        return new ClassFileImporter().importPackages("com.yliu22520.iotota");
    }

    public static final ArchRule simulatorMustNotDependOnOtherBusinessModules = classes()
            .that().resideInAnyPackage("..simulator..")
            .should().onlyDependOnClassesThat()
            .resideOutsideOfPackages("..diagnosis..", "..knowledge..", "..action..", "..identity..", "..audit..");

    public static final ArchRule identityMustNotDependOnAction = classes()
            .that().resideInAnyPackage("..identity..")
            .should().onlyDependOnClassesThat()
            .resideOutsideOfPackage("..action..");

    public static final ArchRule diagnosisMustNotDependOnActionImplementation = classes()
            .that().resideInAnyPackage("..diagnosis..")
            .should().onlyDependOnClassesThat()
            .resideOutsideOfPackage("..action..");

    public static final ArchRule knowledgeMustNotDependOnActionImplementation = classes()
            .that().resideInAnyPackage("..knowledge..")
            .should().onlyDependOnClassesThat()
            .resideOutsideOfPackage("..action..");

    public static final ArchRule actionMustNotDependOnIdentityImplementation = classes()
            .that().resideInAnyPackage("..action..")
            .should().onlyDependOnClassesThat()
            .resideOutsideOfPackage("..identity..");

    public static final ArchRule businessModulesMustNotDependOnTheSpringBootEntryPoint = noClasses()
            .that().resideInAnyPackage("..simulator..", "..diagnosis..", "..knowledge..", "..action..", "..identity..", "..audit..")
            .should().dependOnClassesThat().haveFullyQualifiedName(IotOtaOperationsApplication.class.getName());

    @Test
    void simulatorBoundaryIsEnforced() {
        simulatorMustNotDependOnOtherBusinessModules.check(importedClasses());
    }

    @Test
    void identityBoundaryIsEnforced() {
        identityMustNotDependOnAction.check(importedClasses());
    }

    @Test
    void entryPointIsNotUsedByBusinessModules() {
        businessModulesMustNotDependOnTheSpringBootEntryPoint.check(importedClasses());
    }

    @Test
    void workflowBoundariesDoNotPointAtActionOrIdentityImplementations() {
        diagnosisMustNotDependOnActionImplementation.check(importedClasses());
        knowledgeMustNotDependOnActionImplementation.check(importedClasses());
        actionMustNotDependOnIdentityImplementation.check(importedClasses());
    }
}
