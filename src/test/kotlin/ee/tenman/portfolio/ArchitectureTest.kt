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
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices
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
  val `should use TimeUtility for timing instead of System nanoTime or currentTimeMillis`: ArchRule =
    noClasses()
      .that()
      .doNotHaveSimpleName("TimeUtility")
      .should(callSystemTimingMethods())
      .because("Use TimeUtility for consistent timing across the codebase")

  private fun callSystemTimingMethods(): ArchCondition<JavaClass> =
    object : ArchCondition<JavaClass>("call System.nanoTime() or System.currentTimeMillis()") {
      override fun check(
        javaClass: JavaClass,
        events: ConditionEvents,
      ) {
        javaClass.methodCallsFromSelf.forEach { call ->
          val targetOwner = call.targetOwner.name
          val targetName = call.target.name
          if (targetOwner == "java.lang.System" && (targetName == "nanoTime" || targetName == "currentTimeMillis")) {
            events.add(
              SimpleConditionEvent.violated(
                call,
                "${javaClass.name} calls System.$targetName() directly - use TimeUtility instead",
              ),
            )
          }
        }
      }
    }

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
  fun logStatementsShouldUseStringInterpolationNotParameterizedFormat(classes: JavaClasses) {
    val logMethods = setOf("trace", "debug", "info", "warn", "error")
    val violations = mutableListOf<String>()
    classes.forEach { javaClass ->
      javaClass.methodCallsFromSelf
        .filter { call ->
          call.targetOwner.name == "org.slf4j.Logger" && logMethods.contains(call.target.name)
        }.forEach { call ->
          val paramTypes = call.target.rawParameterTypes.map { it.name }
          val hasMultipleParams = paramTypes.size >= 2 && paramTypes[0] == "java.lang.String"
          val hasObjectParams = paramTypes.drop(1).any { it == "java.lang.Object" || it == "[Ljava.lang.Object;" }
          val isNotThrowableOnly = !(paramTypes.size == 2 && paramTypes[1] == "java.lang.Throwable")
          if (hasMultipleParams && hasObjectParams && isNotThrowableOnly) {
            violations.add("${javaClass.simpleName} at ${call.sourceCodeLocation}")
          }
        }
    }
    if (violations.isNotEmpty()) {
      throw AssertionError(
        "Use Kotlin string interpolation (\$variable) instead of SLF4J placeholders ({}):\n" +
          violations.joinToString("\n") { "  - $it" },
      )
    }
  }

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
  fun timeMethodsShouldUseClockParameter(classes: JavaClasses) {
    val timeClasses =
      mapOf(
        "java.time.LocalDate" to "now",
        "java.time.LocalDateTime" to "now",
        "java.time.Instant" to "now",
        "java.time.ZonedDateTime" to "now",
      )
    val violations = mutableListOf<String>()
    classes.forEach { javaClass ->
      javaClass.methodCallsFromSelf
        .filter { call ->
          val targetClass = call.targetOwner.name
          val targetMethod = call.target.name
          timeClasses[targetClass] == targetMethod
        }.forEach { call ->
          val paramTypes = call.target.rawParameterTypes.map { it.name }
          val hasNoParams = paramTypes.isEmpty()
          val hasOnlyZoneId = paramTypes.size == 1 && paramTypes[0] == "java.time.ZoneId"
          if (hasNoParams || hasOnlyZoneId) {
            violations.add(
              "${javaClass.simpleName}.${call.target.name}() at ${call.sourceCodeLocation} - use Clock parameter",
            )
          }
        }
    }
    if (violations.isNotEmpty()) {
      throw AssertionError(
        "Time methods should use Clock parameter for testability:\n" +
          violations.joinToString("\n") { "  - $it" },
      )
    }
  }

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

  @ArchTest
  fun testsShouldNotUseWhenExpressions(classes: JavaClasses) {
    val violations =
      classes
        .filter { javaClass ->
          javaClass.simpleName == "WhenMappings" &&
            javaClass.packageName.startsWith("ee.tenman.portfolio") &&
            (
              javaClass.enclosingClass
                .orElse(null)
                ?.simpleName
                ?.endsWith("Test") == true ||
              javaClass.enclosingClass
                .orElse(null)
                ?.simpleName
                ?.endsWith("IT") == true
            )
        }.map { javaClass ->
          val enclosingClass = javaClass.enclosingClass.orElse(null)?.simpleName ?: "unknown"
          "$enclosingClass uses 'when' expression - prefer explicit test assertions with CsvSource"
        }
    if (violations.isNotEmpty()) {
      throw AssertionError(
        "Tests should not use 'when' expressions as they duplicate logic:\n" +
          violations.joinToString("\n") { "  - $it" },
      )
    }
  }

  @ArchTest
  fun serviceMethodsShouldNotHaveTooManyDependencies(classes: JavaClasses) {
    val maxDependencies = 65
    val violations = mutableListOf<String>()
    classes
      .filter { javaClass ->
        javaClass.packageName.startsWith("ee.tenman.portfolio.service") &&
          !javaClass.simpleName.endsWith("Test") &&
          !javaClass.simpleName.endsWith("IT") &&
          !javaClass.simpleName.contains("$") &&
          javaClass.isAnnotatedWith(Service::class.java)
      }.forEach { javaClass ->
        javaClass.methods
          .filter { method ->
            !method.name.startsWith("access$") &&
              !method.name.contains("$") &&
              !method.name.startsWith("component") &&
              method.name != "copy" &&
              method.name != "equals" &&
              method.name != "hashCode" &&
              method.name != "toString"
          }.forEach { method ->
            val dependencies = method.methodCallsFromSelf.size + method.fieldAccesses.size
            if (dependencies > maxDependencies) {
              violations.add("${javaClass.simpleName}.${method.name}() has $dependencies dependencies - consider extracting helper methods")
            }
          }
      }
    if (violations.isNotEmpty()) {
      throw AssertionError(
        "Service methods should not have more than $maxDependencies dependencies:\n" +
          violations.joinToString("\n") { "  - $it" },
      )
    }
  }

  @ArchTest
  fun servicesShouldNotHaveTooManyMethods(classes: JavaClasses) {
    val maxMethods = 25
    val violations = mutableListOf<String>()
    classes
      .filter { javaClass ->
        javaClass.packageName.startsWith("ee.tenman.portfolio.service") &&
          !javaClass.simpleName.endsWith("Test") &&
          !javaClass.simpleName.endsWith("IT") &&
          !javaClass.simpleName.contains("$") &&
          javaClass.isAnnotatedWith(Service::class.java)
      }.forEach { javaClass ->
        val publicMethods =
          javaClass.methods.filter { method ->
          method.modifiers.contains(com.tngtech.archunit.core.domain.JavaModifier.PUBLIC) &&
            !method.name.startsWith("access$") &&
            !method.name.contains("$") &&
            !method.name.startsWith("component") &&
            method.name != "copy" &&
            method.name != "equals" &&
            method.name != "hashCode" &&
            method.name != "toString"
        }
        if (publicMethods.size > maxMethods) {
          violations.add("${javaClass.simpleName} has ${publicMethods.size} public methods (max $maxMethods) - consider splitting")
        }
      }
    if (violations.isNotEmpty()) {
      throw AssertionError(
        "Services should not have more than $maxMethods public methods:\n" +
          violations.joinToString("\n") { "  - $it" },
      )
    }
  }

  @ArchTest
  fun serviceConstructorsShouldNotHaveMoreThanEightParameters(classes: JavaClasses) {
    val maxParams = 8
    val violations = mutableListOf<String>()
    classes
      .filter { javaClass ->
        javaClass.packageName.startsWith("ee.tenman.portfolio.service") &&
          !javaClass.simpleName.endsWith("Test") &&
          !javaClass.simpleName.endsWith("IT") &&
          !javaClass.simpleName.contains("$") &&
          !javaClass.isInterface &&
          javaClass.isAnnotatedWith(Service::class.java)
      }.forEach { javaClass ->
        javaClass.constructors.forEach { constructor ->
          val paramCount = constructor.rawParameterTypes.size
          if (paramCount > maxParams) {
            violations.add("${javaClass.simpleName} constructor has $paramCount parameters (max $maxParams)")
          }
        }
      }
    if (violations.isNotEmpty()) {
      throw AssertionError(
        "Service constructors should not have more than $maxParams parameters. Consider splitting the service:\n" +
          violations.joinToString("\n") { "  - $it" },
      )
    }
  }

  @ArchTest
  fun controllersShouldBeFreeOfCircularDependencies(classes: JavaClasses) {
    slices()
      .matching("ee.tenman.portfolio.controller.(*)..")
      .should()
      .beFreeOfCycles()
      .allowEmptyShould(true)
      .because("Circular dependencies between controller packages indicate poor design")
      .check(classes)
  }

  @ArchTest
  fun repositoryMethodsShouldFollowNamingConventions(classes: JavaClasses) {
    val validPrefixes =
      setOf(
        "find",
        "get",
        "read",
        "query",
        "search",
        "stream",
        "count",
        "exists",
        "save",
        "insert",
        "update",
        "upsert",
        "delete",
        "remove",
        "flush",
        "refresh",
      )
    val violations = mutableListOf<String>()
    classes
      .filter { javaClass ->
        javaClass.simpleName.endsWith("Repository") &&
          javaClass.packageName.startsWith("ee.tenman.portfolio") &&
          javaClass.isInterface
      }.forEach { repository ->
        repository.methods
          .filter { method ->
            !method.name.startsWith("$") &&
              method.owner.name == repository.name &&
              !isSpringDataMethod(method.name)
          }.forEach { method ->
            val hasValidPrefix = validPrefixes.any { prefix -> method.name.startsWith(prefix) }
            if (!hasValidPrefix) {
              violations.add("${repository.simpleName}.${method.name}() should start with: ${validPrefixes.joinToString(", ")}")
            }
          }
      }
    if (violations.isNotEmpty()) {
      throw AssertionError(
        "Repository methods should follow Spring Data naming conventions:\n" +
          violations.joinToString("\n") { "  - $it" },
      )
    }
  }

  private fun isSpringDataMethod(methodName: String): Boolean {
    val springDataMethods =
      setOf(
      "findAll",
        "findById",
        "save",
        "saveAll",
        "deleteById",
        "delete",
        "deleteAll",
      "count",
        "existsById",
        "flush",
        "saveAndFlush",
        "deleteAllInBatch",
        "getById",
      "getReferenceById",
        "findAllById",
        "deleteAllById",
        "deleteAllByIdInBatch",
    )
    return springDataMethods.contains(methodName)
  }

  @ArchTest
  fun controllerMethodsShouldNotHaveMoreThanFiveParameters(classes: JavaClasses) {
    val maxParams = 5
    val violations = mutableListOf<String>()
    classes
      .filter { javaClass ->
        javaClass.packageName.startsWith("ee.tenman.portfolio.controller") &&
          javaClass.isAnnotatedWith(RestController::class.java)
      }.forEach { javaClass ->
        javaClass.methods
          .filter { method ->
            method.modifiers.contains(com.tngtech.archunit.core.domain.JavaModifier.PUBLIC) &&
              !method.name.startsWith("access$") &&
              !method.name.contains("$")
          }.forEach { method ->
            val paramCount = method.rawParameterTypes.size
            if (paramCount > maxParams) {
              violations.add("${javaClass.simpleName}.${method.name}() has $paramCount parameters (max $maxParams)")
            }
          }
      }
    if (violations.isNotEmpty()) {
      throw AssertionError(
        "Controller methods should not have more than $maxParams parameters. Consider using a request DTO:\n" +
          violations.joinToString("\n") { "  - $it" },
      )
    }
  }
}
