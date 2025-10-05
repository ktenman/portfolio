package ee.tenman.portfolio

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields
import com.tngtech.archunit.library.Architectures.layeredArchitecture
import com.tngtech.archunit.library.GeneralCodingRules
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.RestController

@AnalyzeClasses(packages = ["ee.tenman.portfolio"])
class ArchitectureTest {
  @ArchTest
  val `should enforce layered architecture`: ArchRule =
    layeredArchitecture()
      .consideringOnlyDependenciesInLayers()
      .layer("Controller")
      .definedBy("..controller..")
      .layer("Service")
      .definedBy("..service..")
      .layer("Repository")
      .definedBy("..repository..")
      .layer("Domain")
      .definedBy("..domain..")
      .whereLayer("Controller")
      .mayNotBeAccessedByAnyLayer()
      .whereLayer("Service")
      .mayOnlyBeAccessedByLayers("Controller", "Service")
      .whereLayer("Repository")
      .mayOnlyBeAccessedByLayers("Service")
      .ignoreDependency(
        DescribedPredicate.describe("test classes") { javaClass: JavaClass ->
          javaClass.name.endsWith("IT") || javaClass.name.contains("Test")
        },
        DescribedPredicate.alwaysTrue(),
      )

  @ArchTest
  val `should have Service suffix for classes in service package annotated with Service`: ArchRule =
    classes()
      .that()
      .resideInAPackage("..service..")
      .and()
      .areAnnotatedWith(Service::class.java)
      .should()
      .haveSimpleNameEndingWith("Service")

  @ArchTest
  val `should have Repository suffix for classes in repository package`: ArchRule =
    classes()
      .that()
      .resideInAPackage("..repository..")
      .should()
      .haveSimpleNameEndingWith("Repository")

  @ArchTest
  val `should have Controller suffix for classes annotated with RestController`: ArchRule =
    classes()
      .that()
      .areAnnotatedWith(RestController::class.java)
      .should()
      .haveSimpleNameEndingWith("Controller")
      .andShould()
      .resideInAPackage("..controller..")

  @ArchTest
  val `should not use field injection`: ArchRule =
    noFields()
      .should()
      .beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
      .because("Field injection is discouraged, use constructor injection instead")

  @ArchTest
  val `should annotate service classes with Service annotation`: ArchRule =
    classes()
      .that()
      .resideInAPackage("..service..")
      .and()
      .haveSimpleNameEndingWith("Service")
      .should()
      .beAnnotatedWith(Service::class.java)

  @ArchTest
  val `should make repositories interfaces`: ArchRule =
    classes()
      .that()
      .resideInAPackage("..repository..")
      .and()
      .haveSimpleNameEndingWith("Repository")
      .should()
      .beInterfaces()

  @ArchTest
  val `should not use Java util logging`: ArchRule = GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING

  @ArchTest
  val `should not use System out`: ArchRule =
    noClasses()
      .should()
      .callMethod(System::class.java, "out")
      .because("Use proper logging instead of System.out")
}
