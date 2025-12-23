package ee.tenman.portfolio

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.domain.JavaMethod
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
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
import org.springframework.cache.annotation.CachePut
import org.springframework.cache.annotation.Cacheable
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
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
      .definedBy(
        "..repository..",
        "..binance..",
        "..ft..",
        "..googlevision..",
        "..telegram..",
        "..openrouter..",
        "..lightyear..",
        "..veego..",
      ).layer("Configuration")
      .definedBy("..configuration..")
      .layer("Jobs")
      .definedBy("..job..")
      .layer("Scheduler")
      .definedBy("..scheduler..")
      .layer("DTOs")
      .definedBy("..dto..")
      .layer("Model")
      .definedBy("..model..")
      .whereLayer("API")
      .mayOnlyBeAccessedByLayers("Configuration")
      .whereLayer("Application")
      .mayOnlyBeAccessedByLayers("API", "Jobs", "Scheduler", "Configuration", "Application", "Model", "DTOs", "Infrastructure")
      .whereLayer("Domain")
      .mayOnlyBeAccessedByLayers("Application", "Infrastructure", "API", "DTOs", "Jobs", "Configuration", "Model")
      .whereLayer("Infrastructure")
      .mayOnlyBeAccessedByLayers("Application", "Jobs", "Configuration")
      .whereLayer("Jobs")
      .mayOnlyBeAccessedByLayers("Configuration", "API", "Application")
      .whereLayer("Scheduler")
      .mayOnlyBeAccessedByLayers("Jobs", "Configuration", "Application")
      .whereLayer("DTOs")
      .mayOnlyBeAccessedByLayers("API", "Application", "Infrastructure")
      .whereLayer("Model")
      .mayOnlyBeAccessedByLayers("API", "Application", "Infrastructure", "DTOs", "Jobs")
      .whereLayer("Configuration")
      .mayOnlyBeAccessedByLayers("API", "Application", "Infrastructure", "Jobs")
      .ignoreDependency(
        DescribedPredicate.describe("test classes and fixtures") { javaClass: JavaClass ->
          javaClass.name.endsWith("IT") ||
            javaClass.name.endsWith("Test") ||
            javaClass.name.endsWith("IntegrationTest") ||
            javaClass.packageName.contains(".testing.")
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
        "tools.jackson..",
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
      .resideInAnyPackage("..binance..", "..ft..", "..googlevision..", "..telegram..", "..veego..")
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
  val `no lazy injection should be used as it hides design issues`: ArchRule =
    noFields()
      .should()
      .beAnnotatedWith(Lazy::class.java)
      .because("@Lazy hides circular dependencies and makes code harder to test")

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
  val `cache annotations should only be on service or infrastructure methods`: ArchRule =
    methods()
      .that()
      .areAnnotatedWith(Cacheable::class.java)
      .or()
      .areAnnotatedWith(CacheEvict::class.java)
      .or()
      .areAnnotatedWith(CachePut::class.java)
      .should()
      .beDeclaredInClassesThat()
      .resideInAnyPackage("..service..", "..lightyear..", "..binance..", "..ft..", "..openrouter..")
      .because("Caching is a service or infrastructure layer concern")

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

  @ArchTest
  val `data classes should be in model package not service package`: ArchRule =
    noClasses()
      .that()
      .haveSimpleNameEndingWith("Metrics")
      .should()
      .resideInAPackage("ee.tenman.portfolio.service")
      .because("Data classes like *Metrics should be in model package for better organization")

  @ArchTest
  fun eachSourceFileShouldContainOnlyOneTopLevelClass(classes: JavaClasses) {
    val violations = findMultipleClassesPerFileViolations(classes)
    if (violations.isNotEmpty()) {
      throw AssertionError(
        "Each file should contain only one top-level class. Violations:\n" +
          violations.joinToString("\n") { "  - $it" },
      )
    }
  }

  private fun findMultipleClassesPerFileViolations(classes: JavaClasses): List<String> {
    val topLevelClasses: Map<String, List<JavaClass>> =
      classes
      .filter { isRelevantTopLevelClass(it) }
      .groupBy { javaClass -> extractFileName(javaClass) }
    return topLevelClasses.entries
      .filter { entry ->
        !isAcceptableMultiClassFile(entry.key) && filterKotlinFileClasses(entry.value).size > 1
      }.map { entry ->
        val classNames = filterKotlinFileClasses(entry.value).joinToString(", ") { it.simpleName }
        "${entry.key} contains multiple classes: [$classNames]"
      }
  }

  private fun extractFileName(javaClass: JavaClass): String {
    val source = javaClass.source
    if (!source.isPresent) return "unknown"
    val fileName = source.get().fileName
    return if (fileName.isPresent) fileName.get() else "unknown"
  }

  private fun isAcceptableMultiClassFile(fileName: String): Boolean =
    fileName.endsWith("Response.kt") ||
      fileName.endsWith("Request.kt") ||
      fileName.endsWith("Properties.kt") ||
      fileName.endsWith("Client.kt") ||
      fileName.endsWith("Test.kt") ||
      fileName.endsWith("IT.kt") ||
      fileName.endsWith("Utility.kt") ||
      fileName.endsWith("Controller.kt") ||
      fileName.endsWith("Handler.kt") ||
      fileName.endsWith("Indicator.kt") ||
      fileName.endsWith("Processor.kt")

  private fun isRelevantTopLevelClass(javaClass: JavaClass): Boolean =
    !javaClass.isInnerClass &&
      !javaClass.isAnonymousClass &&
      !javaClass.simpleName.contains("$") &&
      javaClass.packageName.startsWith("ee.tenman.portfolio")

  private fun filterKotlinFileClasses(classes: List<JavaClass>): List<JavaClass> =
    classes.filter { javaClass ->
      !javaClass.simpleName.endsWith("Kt") &&
        javaClass.simpleName != "Companion" &&
        javaClass.simpleName != "DefaultImpls" &&
        javaClass.simpleName != "WhenMappings"
    }
}
