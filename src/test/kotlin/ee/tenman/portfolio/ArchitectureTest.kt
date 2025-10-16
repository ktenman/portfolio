package ee.tenman.portfolio

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaMethod
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods
import com.tngtech.archunit.library.Architectures.layeredArchitecture
import com.tngtech.archunit.library.GeneralCodingRules
import jakarta.persistence.Entity
import org.slf4j.Logger
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.RestController

@AnalyzeClasses(packages = ["ee.tenman.portfolio"])
class ArchitectureTest {
  @ArchTest
  val `should enforce hexagonal architecture with clean boundaries`: ArchRule =
    layeredArchitecture()
      .consideringAllDependencies()
      .layer("API")
      .definedBy("..controller..")
      .layer("Application")
      .definedBy("..service..")
      .layer("Domain")
      .definedBy("..domain..")
      .layer("Infrastructure")
      .definedBy("..repository..", "..alphavantage..", "..binance..", "..ft..", "..googlevision..", "..telegram..")
      .layer("Configuration")
      .definedBy("..configuration..")
      .layer("Jobs")
      .definedBy("..job..")
      .layer("DTOs")
      .definedBy("..dto..")
      .whereLayer("API")
      .mayOnlyBeAccessedByLayers("Configuration")
      .whereLayer("Application")
      .mayOnlyBeAccessedByLayers("API", "Jobs", "Configuration", "Application")
      .whereLayer("Domain")
      .mayOnlyBeAccessedByLayers("Application", "Infrastructure", "API", "DTOs", "Jobs", "Configuration")
      .whereLayer("Infrastructure")
      .mayOnlyBeAccessedByLayers("Application", "Jobs", "Configuration")
      .whereLayer("Jobs")
      .mayOnlyBeAccessedByLayers("Configuration", "API", "Application")
      .whereLayer("DTOs")
      .mayOnlyBeAccessedByLayers("API", "Application", "Infrastructure")
      .whereLayer("Configuration")
      .mayOnlyBeAccessedByLayers("API", "Application", "Infrastructure", "Jobs")
      .ignoreDependency(
        DescribedPredicate.describe("test classes") { javaClass: JavaClass ->
          javaClass.name.endsWith("IT") || javaClass.name.endsWith("Test") || javaClass.name.endsWith("IntegrationTest")
        },
        DescribedPredicate.alwaysTrue(),
      )

  @ArchTest
  val `domain entities should be pure business objects without framework dependencies`: ArchRule =
    classes()
      .that()
      .resideInAPackage("..domain..")
      .should()
      .onlyDependOnClassesThat()
      .resideInAnyPackage(
        "..domain..",
        "java..",
        "kotlin..",
        "jakarta.persistence..",
        "jakarta.validation..",
        "org.hibernate.annotations..",
        "com.fasterxml.jackson..",
        "java.time..",
        "java.math..",
        "org.jetbrains.annotations..",
      ).because(
        "Domain entities should remain framework-agnostic except for necessary " +
          "persistence, validation, and nullability annotations",
      )

  @ArchTest
  val `controllers should only call services never repositories directly`: ArchRule =
    noClasses()
      .that()
      .resideInAPackage("..controller..")
      .and()
      .haveSimpleNameNotEndingWith("IT")
      .and()
      .haveSimpleNameNotEndingWith("Test")
      .should()
      .dependOnClassesThat()
      .resideInAPackage("..repository..")
      .because("Controllers should delegate to services for business logic and data access")

  @ArchTest
  val `services should handle transactional boundaries`: ArchRule =
    methods()
      .that()
      .areAnnotatedWith(Transactional::class.java)
      .should()
      .beDeclaredInClassesThat()
      .resideInAPackage("..service..")
      .orShould()
      .beDeclaredInClassesThat()
      .resideInAPackage("..job..")
      .because("Transaction management belongs in the service or job layer")

  @ArchTest
  val `repositories should be interfaces extending Spring Data repositories`: ArchRule =
    classes()
      .that()
      .resideInAPackage("..repository..")
      .and()
      .haveSimpleNameEndingWith("Repository")
      .should()
      .beInterfaces()
      .because("Repositories should be Spring Data interfaces for clean abstraction")

  @ArchTest
  val `DTOs should be data classes with immutable fields`: ArchRule =
    classes()
      .that()
      .resideInAPackage("..dto..")
      .and()
      .haveSimpleNameEndingWith("Dto")
      .should()
      .haveOnlyFinalFields()
      .because("DTOs should be immutable data carriers")

  @ArchTest
  val `configuration classes should be in configuration package`: ArchRule =
    classes()
      .that()
      .areAnnotatedWith(Configuration::class.java)
      .should()
      .resideInAPackage("..configuration..")
      .because("All Spring configuration should be centralized")

  @ArchTest
  val `scheduled jobs should be in job package`: ArchRule =
    methods()
      .that()
      .areAnnotatedWith(Scheduled::class.java)
      .should()
      .beDeclaredInClassesThat()
      .resideInAPackage("..job..")
      .orShould()
      .beDeclaredInClassesThat()
      .resideInAPackage("..scheduler..")
      .because("Scheduled tasks should be organized in dedicated packages")

  @ArchTest
  val `services should follow naming convention with Service suffix`: ArchRule =
    classes()
      .that()
      .resideInAPackage("..service..")
      .and()
      .areAnnotatedWith(Service::class.java)
      .should()
      .haveSimpleNameEndingWith("Service")

  @ArchTest
  val `controllers should follow naming convention with Controller suffix`: ArchRule =
    classes()
      .that()
      .areAnnotatedWith(RestController::class.java)
      .should()
      .haveSimpleNameEndingWith("Controller")
      .andShould()
      .resideInAPackage("..controller..")

  @ArchTest
  val `clients should follow naming convention with Client suffix`: ArchRule =
    classes()
      .that()
      .resideInAnyPackage("..alphavantage..", "..binance..", "..ft..", "..googlevision..", "..telegram..")
      .and()
      .haveSimpleNameEndingWith("Client")
      .should()
      .beAnnotatedWith(Component::class.java)
      .orShould()
      .beAnnotatedWith(Service::class.java)
      .orShould()
      .beAnnotatedWith("org.springframework.cloud.openfeign.FeignClient")

  @ArchTest
  val `no field injection should be used prefer constructor injection`: ArchRule =
    noFields()
      .should()
      .beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
      .because("Constructor injection provides better testability and immutability")

  @ArchTest
  val `loggers should be private final`: ArchRule =
    fields()
      .that()
      .haveRawType(Logger::class.java)
      .should()
      .bePrivate()
      .andShould()
      .beFinal()
      .because("Loggers should be private and immutable (Kotlin doesn't require static)")

  @ArchTest
  val `no System out or err should be used`: ArchRule =
    GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS
      .because("Use SLF4J logging instead of System.out/err")

  @ArchTest
  val `no java util logging should be used`: ArchRule =
    GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING
      .because("Use SLF4J as the logging facade")

  @ArchTest
  val `exceptions should be in their own package`: ArchRule =
    classes()
      .that()
      .haveSimpleNameEndingWith("Exception")
      .and()
      .areNotMemberClasses()
      .should()
      .resideInAPackage("..exception..")
      .orShould()
      .resideInAPackage("..configuration..")
      .because("Exceptions should be organized for easy discovery")

  @ArchTest
  val `utility classes should have Util suffix`: ArchRule =
    classes()
      .that()
      .resideInAPackage("..util..")
      .should()
      .haveSimpleNameEndingWith("Util")
      .allowEmptyShould(true)
      .because("Utility classes should follow naming conventions")

  @ArchTest
  val `Spring Boot application class should be in root package`: ArchRule =
    classes()
      .that()
      .areAnnotatedWith(SpringBootApplication::class.java)
      .should()
      .resideInAPackage("ee.tenman.portfolio")
      .because("Main application class should be at the root for component scanning")

  @ArchTest
  val `entities should have proper JPA annotations`: ArchRule =
    classes()
      .that()
      .areAnnotatedWith(Entity::class.java)
      .should()
      .resideInAPackage("..domain..")
      .because("JPA entities are part of the domain model")

  @ArchTest
  val `cache annotations should only be on service methods`: ArchRule =
    methods()
      .that()
      .areAnnotatedWith(Cacheable::class.java)
      .or()
      .areAnnotatedWith(CacheEvict::class.java)
      .should()
      .beDeclaredInClassesThat()
      .resideInAPackage("..service..")
      .because("Caching is a service layer concern")

  @ArchTest
  val `interfaces should not have I prefix`: ArchRule =
    noClasses()
      .that()
      .areInterfaces()
      .and()
      .haveSimpleNameStartingWith("I")
      .and()
      .doNotHaveSimpleName("IntegrationTest")
      .and()
      .doNotHaveSimpleName("InstrumentRepository")
      .should()
      .haveSimpleNameStartingWith("I")
      .allowEmptyShould(true)
      .because("Kotlin and modern Java don't use Hungarian notation")

  @ArchTest
  val `test classes should end with Test or IT`: ArchRule =
    classes()
      .that()
      .resideInAPackage("..test..")
      .should()
      .haveSimpleNameEndingWith("Test")
      .orShould()
      .haveSimpleNameEndingWith("IT")
      .allowEmptyShould(true)
      .because("Test naming convention for easy identification")

  @ArchTest
  val `services should not depend on controllers`: ArchRule =
    noClasses()
      .that()
      .resideInAPackage("..service..")
      .should()
      .dependOnClassesThat()
      .resideInAPackage("..controller..")
      .because("Services should not know about the web layer")

  @ArchTest
  val `domain should not depend on application services`: ArchRule =
    noClasses()
      .that()
      .resideInAPackage("..domain..")
      .should()
      .dependOnClassesThat()
      .resideInAPackage("..service..")
      .because("Domain layer should be independent of application services")

  @ArchTest
  val `REST endpoints should return DTOs not entities`: ArchRule =
    noMethods()
      .that()
      .areDeclaredInClassesThat()
      .areAnnotatedWith(RestController::class.java)
      .and()
      .arePublic()
      .should(
        object : com.tngtech.archunit.lang.ArchCondition<JavaMethod>("return domain entities") {
        override fun check(
          method: JavaMethod,
          events: com.tngtech.archunit.lang.ConditionEvents,
        ) {
          val returnType = method.rawReturnType
          if (returnType.packageName.contains(".domain.") &&
            !returnType.simpleName.endsWith("Dto") &&
            !returnType.simpleName.equals("ResponseEntity")
          ) {
            events.add(
              com.tngtech.archunit.lang.SimpleConditionEvent.violated(
              method,
              "${method.fullName} returns domain entity ${returnType.simpleName} instead of DTO",
            ),
                )
          }
        }
      },
          ).because("API should expose DTOs for decoupling and versioning")
}
