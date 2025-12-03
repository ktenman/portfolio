plugins {
  alias(libs.plugins.spring.boot)
  alias(libs.plugins.spring.dependency.management)
  alias(libs.plugins.kotlin.jpa)
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.spring)
  id("jacoco")
  alias(libs.plugins.ktlint)
  alias(libs.plugins.detekt)
  alias(libs.plugins.typescript.generator)
}

group = "ee.tenman"
version = "0.0.1-SNAPSHOT"

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(21)
  }
}

repositories {
  mavenCentral()
  maven { url = uri("https://djl.ai/maven") }
  maven { url = uri("https://repo.spring.io/milestone") }
}

dependencies {
  implementation(libs.springdoc.openapi.starter.webmvc.ui)
  implementation(libs.grpc.netty.shaded)
  implementation(libs.jsoup)
  implementation(libs.spring.boot.starter.data.jpa)
  implementation(libs.spring.boot.starter.validation)
  implementation(libs.spring.boot.starter.data.redis)
  implementation(libs.spring.boot.starter.webmvc)
  implementation(libs.spring.cloud.starter.openfeign)
  implementation(libs.jackson.module.kotlin)
  implementation(libs.spring.boot.jackson2)
  implementation(libs.jackson2.module.kotlin)
  implementation(libs.jackson2.datatype.jsr310)

  // Security fixes for CVEs
  implementation(libs.commons.fileupload)
  implementation(libs.commons.lang3)
  implementation(libs.spring.boot.starter.actuator)
  implementation(libs.spring.boot.starter.aspectj)
  implementation(libs.micrometer.registry.prometheus)
  implementation(libs.selenide)
  implementation(libs.spring.boot.starter.flyway)
  implementation(libs.flyway.database.postgresql)
  implementation(libs.kotlin.reflect)
  implementation(libs.spring.retry)
  implementation(libs.resilience4j.circuitbreaker)
  implementation(libs.resilience4j.retry)
  implementation(libs.resilience4j.kotlin)
  implementation(libs.guava)
  implementation(libs.commons.math3)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.telegrambots)
  implementation(libs.telegrambots.spring.boot.starter)

  implementation(libs.google.cloud.vision) {
    exclude(group = "commons-logging", module = "commons-logging")
  }
  implementation(libs.minio)

  developmentOnly(libs.spring.boot.docker.compose)
  runtimeOnly(libs.postgresql)
  testImplementation(libs.spring.boot.starter.test) {
    exclude(group = "org.mockito", module = "mockito-core")
    exclude(group = "org.mockito", module = "mockito-junit-jupiter")
    exclude(group = "org.assertj", module = "assertj-core")
  }
  testImplementation(libs.spring.boot.starter.webmvc.test)
  testImplementation(libs.spring.boot.testcontainers)
  testImplementation(libs.kotlin.test.junit5)
  testImplementation(libs.wiremock.spring.boot)
  testImplementation(libs.testcontainers.junit.jupiter)
  testImplementation(libs.junit.jupiter.api)
  testImplementation(libs.testcontainers.postgresql)
  testImplementation(libs.testcontainers.minio)
  testImplementation(libs.mockk)
  testImplementation(libs.spring.mockk)
  testImplementation(libs.archunit.junit5)
  testImplementation(libs.atrium.fluent) {
    exclude("org.jetbrains.kotlin")
  }
  testImplementation(libs.datafaker)
  testRuntimeOnly(libs.junit.platform.launcher)
  testRuntimeOnly(libs.junit.jupiter.engine)
}

configurations.all {
  resolutionStrategy {
    force("io.grpc:grpc-netty-shaded:1.77.0")
  }
}

dependencyManagement {
  imports {
    mavenBom(
      libs.spring.cloud.dependencies
      .get()
      .toString(),
        )
  }
}

kotlin {
  compilerOptions {
    freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
  }
}
val isE2ETestEnvironmentEnabled = System.getenv("E2E")?.toBoolean() == true
println("E2E environment variable: $isE2ETestEnvironmentEnabled")

val test by tasks.getting(Test::class) {
  useJUnitPlatform()
  if (isE2ETestEnvironmentEnabled) {
    configureE2ETestEnvironment()
  } else {
    exclude("**/e2e/**")
  }
  finalizedBy(":jacocoTestReport")

  jvmArgs("-Xmx1g")

  reports {
    html.required = true
    junitXml.required = true
  }

  testLogging {
    events("passed", "skipped", "failed")
    showExceptions = true
    showCauses = true
    showStackTraces = true
    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
  }
}

fun Test.configureE2ETestEnvironment() {
  include("**/e2e/**")
  val properties =
    mutableMapOf(
      "webdriver.chrome.logfile" to "build/reports/chromedriver.log",
      "webdriver.chrome.verboseLogging" to "true",
    )
  if (project.hasProperty("headless")) {
    properties["chromeoptions.args"] = "--headless,--no-sandbox,--disable-gpu"
  }
  systemProperties.putAll(properties)
}

val skipJacoco: Boolean = false
val jacocoEnabled: Boolean = true
tasks.withType<JacocoReport> {
  isEnabled = jacocoEnabled
  if (skipJacoco) {
    enabled = false
  }
  reports {
    xml.required = true
    html.required = true
    csv.required = false
  }
}

configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
  version.set(libs.versions.ktlint.get())
  android.set(false)
  ignoreFailures.set(false)
  reporters {
    reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.HTML)
    reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
  }
  filter {
    exclude("**/generated/**")
    include("**/kotlin/**")
  }
}

tasks.named<cz.habarta.typescript.generator.gradle.GenerateTask>("generateTypeScript") {
  jsonLibrary = cz.habarta.typescript.generator.JsonLibrary.jackson2
  classes =
    listOf(
      "ee.tenman.portfolio.dto.InstrumentDto",
      "ee.tenman.portfolio.dto.TransactionRequestDto",
      "ee.tenman.portfolio.dto.TransactionResponseDto",
      "ee.tenman.portfolio.dto.TransactionSummaryDto",
      "ee.tenman.portfolio.dto.TransactionsWithSummaryDto",
      "ee.tenman.portfolio.dto.PortfolioSummaryDto",
      "ee.tenman.portfolio.dto.EtfHoldingBreakdownDto",
      "ee.tenman.portfolio.domain.Platform",
      "ee.tenman.portfolio.domain.ProviderName",
      "ee.tenman.portfolio.domain.TransactionType",
      "ee.tenman.portfolio.domain.PriceChangePeriod",
    )
  outputKind = cz.habarta.typescript.generator.TypeScriptOutputKind.module
  outputFileType = cz.habarta.typescript.generator.TypeScriptFileType.implementationFile
  outputFile = "ui/models/generated/domain-models.ts"
  mapEnum = cz.habarta.typescript.generator.EnumMapping.asEnum
  mapDate = cz.habarta.typescript.generator.DateMapping.asString
  nonConstEnums = true
}

tasks.named("compileKotlin") {
  finalizedBy("generateTypeScript")
}

tasks.named("generateTypeScript") {
  doLast {
    val generatedFile = file("ui/models/generated/domain-models.ts")
    if (generatedFile.exists()) {
      var content = generatedFile.readText()

      // Remove timestamp to prevent unnecessary git diffs
      content =
        content.replace(
        Regex("// Generated using typescript-generator version .+ on .+"),
        "// Generated using typescript-generator (timestamp removed to prevent git churn)",
      )

      // Remove export from DateAsString (internal type)
      content = content.replace("export type DateAsString = string", "type DateAsString = string")

      generatedFile.writeText(content)
      println("Post-processed: Removed timestamp and export from DateAsString")
    }
  }
}

detekt {
  buildUponDefaultConfig = true
  allRules = false
  config.setFrom(files("$projectDir/detekt.yml"))
}

tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
  reports {
    html.required.set(true)
    checkstyle.required.set(true)
    sarif.required.set(false)
    markdown.required.set(false)
  }
}
