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
      .definedBy(
        "..repository..",
        "..binance..",
        "..ft..",
        "..googlevision..",
        "..telegram..",
        "..openrouter..",
        "..lightyear..",
      ).layer("Configuration")
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
      .resideInAnyPackage("..binance..", "..ft..", "..googlevision..", "..telegram..")
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

  @ArchTest
  val `jobs should follow naming convention with Job suffix`: ArchRule =
    classes()
      .that()
      .resideInAPackage("..job..")
      .and()
      .areAnnotatedWith(Component::class.java)
      .and()
      .haveSimpleNameNotEndingWith("Util")
      .and()
      .haveSimpleNameNotEndingWith("Runner")
      .should()
      .haveSimpleNameEndingWith("Job")
      .because("Scheduled job classes should be named with Job suffix per CLAUDE.md")

  @ArchTest
  val `test methods should use backtick naming convention`: ArchRule =
    methods()
      .that()
      .areAnnotatedWith(org.junit.jupiter.api.Test::class.java)
      .should(
        object : com.tngtech.archunit.lang.ArchCondition<JavaMethod>("use backtick naming convention") {
          override fun check(
            method: JavaMethod,
            events: com.tngtech.archunit.lang.ConditionEvents,
          ) {
            val methodName = method.name
            if (!methodName.contains(" ") && !methodName.startsWith("test")) {
              events.add(
                com.tngtech.archunit.lang.SimpleConditionEvent.violated(
                  method,
                  "${method.fullName} should use backtick naming with English sentence",
                ),
              )
            }
          }
        },
      ).because("Test names should be full English sentences per CLAUDE.md testing standards")

  @ArchTest
  val `test method names should not contain apostrophes`: ArchRule =
    methods()
      .that()
      .areAnnotatedWith(org.junit.jupiter.api.Test::class.java)
      .should(
        object : com.tngtech.archunit.lang.ArchCondition<JavaMethod>("not contain apostrophes") {
          override fun check(
            method: JavaMethod,
            events: com.tngtech.archunit.lang.ConditionEvents,
          ) {
            val methodName = method.name
            val forbiddenContractions = listOf("can't", "don't", "won't", "isn't", "doesn't")
            if (forbiddenContractions.any { methodName.contains(it) }) {
              events.add(
                com.tngtech.archunit.lang.SimpleConditionEvent.violated(
                  method,
                  "${method.fullName} contains apostrophe - use 'cannot' not 'can't' per CLAUDE.md",
                ),
              )
            }
          }
        },
      ).because("Test names must spell 'cannot' and 'dont' without apostrophes per CLAUDE.md")

  @ArchTest
  val `no field injection in any class`: ArchRule =
    noFields()
      .should()
      .beAnnotatedWith("jakarta.inject.Inject")
      .because("Constructor injection provides better testability per CLAUDE.md")

  @ArchTest
  val `response classes should be data classes with immutable fields`: ArchRule =
    classes()
      .that()
      .haveSimpleNameEndingWith("Response")
      .and()
      .resideOutsideOfPackages("..domain..", "..googlevision..")
      .should()
      .haveOnlyFinalFields()
      .because("Response classes should be immutable per CLAUDE.md - prefer val over var")

  @ArchTest
  val `request classes should be data classes with immutable fields`: ArchRule =
    classes()
      .that()
      .haveSimpleNameEndingWith("Request")
      .should()
      .haveOnlyFinalFields()
      .because("Request classes should be immutable per CLAUDE.md - prefer val over var")

  @ArchTest
  val `context classes should be data classes`: ArchRule =
    classes()
      .that()
      .haveSimpleNameEndingWith("Context")
      .and()
      .resideInAPackage("..service..")
      .should()
      .haveOnlyFinalFields()
      .because("Context classes should be immutable data carriers")

  @ArchTest
  val `no direct instantiation of domain entities in controllers`: ArchRule =
    noClasses()
      .that()
      .resideInAPackage("..controller..")
      .should()
      .callConstructorWhere(
        object : DescribedPredicate<com.tngtech.archunit.core.domain.JavaConstructorCall>("domain entity constructor") {
          override fun test(call: com.tngtech.archunit.core.domain.JavaConstructorCall): Boolean {
            val target = call.targetOwner
            return target.packageName.contains(".domain.") && target.isAnnotatedWith(Entity::class.java)
          }
        },
      ).because("Controllers should use DTOs and delegate entity creation to services")

  @ArchTest
  val `public methods in services should have clear verb-based names`: ArchRule =
    methods()
      .that()
      .areDeclaredInClassesThat()
      .areAnnotatedWith(Service::class.java)
      .and()
      .arePublic()
      .and()
      .doNotHaveName("toString")
      .and()
      .doNotHaveName("hashCode")
      .and()
      .doNotHaveName("equals")
      .should(
        object : com.tngtech.archunit.lang.ArchCondition<JavaMethod>("have verb-based names") {
          private val verbPrefixes =
            listOf(
            "get",
              "find",
              "fetch",
              "retrieve",
              "load",
              "create",
              "save",
              "store",
              "persist",
            "update",
              "modify",
              "change",
              "set",
              "delete",
              "remove",
              "clear",
              "reset",
            "calculate",
              "compute",
              "process",
              "transform",
              "convert",
              "validate",
              "check",
            "verify",
              "is",
              "has",
              "can",
              "should",
              "send",
              "notify",
              "publish",
              "emit",
            "start",
              "stop",
              "run",
              "execute",
              "invoke",
              "apply",
              "build",
              "generate",
            "parse",
              "format",
              "enrich",
              "filter",
              "sort",
              "group",
              "aggregate",
            "initialize",
              "configure",
              "setup",
              "handle",
              "on",
              "do",
            "predict",
              "detect",
              "classify",
              "evict",
              "logo",
              "upload",
              "cleanup",
              "return",
            "extract",
              "recalculate",
          )

          override fun check(
            method: JavaMethod,
            events: com.tngtech.archunit.lang.ConditionEvents,
          ) {
            val name = method.name
            val startsWithVerb = verbPrefixes.any { name.startsWith(it) }
            if (!startsWithVerb && !name.contains("$")) {
              events.add(
                com.tngtech.archunit.lang.SimpleConditionEvent.violated(
                  method,
                  "${method.fullName} should start with a verb per CLAUDE.md",
                ),
              )
            }
          }
        },
      ).because("Method names should be clear single-word verbs per CLAUDE.md")

  @ArchTest
  val `jobs should not directly call repositories`: ArchRule =
    noClasses()
      .that()
      .resideInAPackage("..job..")
      .and()
      .haveSimpleNameEndingWith("Job")
      .and()
      .doNotHaveSimpleName("EtfHoldingsClassificationJob")
      .should()
      .dependOnClassesThat()
      .resideInAPackage("..repository..")
      .because("Jobs should delegate data access to services")

  @ArchTest
  val `integration tests should use IntegrationTest annotation`: ArchRule =
    classes()
      .that()
      .haveSimpleNameEndingWith("IT")
      .should()
      .beAnnotatedWith("ee.tenman.portfolio.configuration.IntegrationTest")
      .orShould()
      .beInterfaces()
      .because("Integration tests should use @IntegrationTest for proper container setup per CLAUDE.md")

  @ArchTest
  val `companion objects should only contain constants`: ArchRule =
    fields()
      .that()
      .areDeclaredInClassesThat()
      .haveSimpleNameContaining("Companion")
      .should()
      .beFinal()
      .because("Companion object fields should be constants (val) not mutable state")
}
