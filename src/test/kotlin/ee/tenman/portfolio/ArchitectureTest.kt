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
  val layeredArchitectureRule: ArchRule = layeredArchitecture()
    .consideringOnlyDependenciesInLayers()
    .layer("Controller").definedBy("..controller..")
    .layer("Service").definedBy("..service..")
    .layer("Repository").definedBy("..repository..")
    .layer("Domain").definedBy("..domain..")
    .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
    .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller", "Service")
    .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service")
    .ignoreDependency(
      DescribedPredicate.describe("test classes") { javaClass: JavaClass ->
        javaClass.name.endsWith("IT") || javaClass.name.contains("Test")
      },
      DescribedPredicate.alwaysTrue()
    )

  @ArchTest
  val servicesNamingRule: ArchRule = classes()
    .that().resideInAPackage("..service..")
    .and().areAnnotatedWith(Service::class.java)
    .should().haveSimpleNameEndingWith("Service")

  @ArchTest
  val repositoriesNamingRule: ArchRule = classes()
    .that().resideInAPackage("..repository..")
    .should().haveSimpleNameEndingWith("Repository")

  @ArchTest
  val controllersNamingRule: ArchRule = classes()
    .that().areAnnotatedWith(RestController::class.java)
    .should().haveSimpleNameEndingWith("Controller")
    .andShould().resideInAPackage("..controller..")

  @ArchTest
  val noFieldInjectionRule: ArchRule = noFields()
    .should().beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
    .because("Field injection is discouraged, use constructor injection instead")

  @ArchTest
  val servicesShouldBeAnnotatedRule: ArchRule = classes()
    .that().resideInAPackage("..service..")
    .and().haveSimpleNameEndingWith("Service")
    .should().beAnnotatedWith(Service::class.java)

  @ArchTest
  val repositoriesShouldBeInterfacesRule: ArchRule = classes()
    .that().resideInAPackage("..repository..")
    .and().haveSimpleNameEndingWith("Repository")
    .should().beInterfaces()

  @ArchTest
  val noJavaUtilLoggingRule: ArchRule = GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING

  @ArchTest
  val noSystemOutRule: ArchRule = noClasses()
    .should().callMethod(System::class.java, "out")
    .because("Use proper logging instead of System.out")
}
