plugins {
  alias(libs.plugins.spring.boot)
  alias(libs.plugins.spring.dependency.management)
  alias(libs.plugins.kotlin.jpa)
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.spring)
  id("jacoco")
  alias(libs.plugins.ktlint)
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
  implementation(libs.jsoup)
  implementation(libs.spring.boot.starter.data.jpa)
  implementation(libs.spring.boot.starter.validation)
  implementation(libs.spring.boot.starter.data.redis)
  implementation(libs.spring.boot.starter.web)
  implementation(libs.spring.cloud.starter.openfeign)
  implementation(libs.jackson.module.kotlin)

  // Security fixes for CVEs
  implementation(libs.commons.fileupload)
  implementation(libs.commons.lang3)
  implementation(libs.spring.boot.starter.actuator)
  implementation(libs.spring.boot.starter.aop)
  implementation(libs.micrometer.registry.prometheus)
  implementation(libs.selenide)
  implementation(libs.flyway.core)
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

  developmentOnly(libs.spring.boot.docker.compose)
  runtimeOnly(libs.postgresql)
  testImplementation(libs.spring.boot.starter.test) {
    exclude(group = "org.mockito", module = "mockito-core")
    exclude(group = "org.mockito", module = "mockito-junit-jupiter")
    exclude(group = "org.assertj", module = "assertj-core")
  }
  testImplementation(libs.spring.boot.testcontainers)
  testImplementation(libs.kotlin.test.junit5)
  testImplementation(libs.spring.cloud.starter.contract.stub.runner) {
    exclude(group = "org.mockito")
    exclude(group = "org.assertj", module = "assertj-core")
    exclude(group = "net.javacrumbs.json-unit", module = "json-unit-assertj")
  }
  testImplementation(libs.testcontainers.junit.jupiter)
  testImplementation(libs.junit.jupiter.api)
  testImplementation(libs.testcontainers.postgresql)
  testImplementation(libs.mockk)
  testImplementation(libs.spring.mockk)
  testImplementation(libs.archunit.junit5)
  testImplementation(libs.atrium.fluent) {
    exclude("org.jetbrains.kotlin")
  }
  testRuntimeOnly(libs.junit.platform.launcher)
  testRuntimeOnly(libs.junit.jupiter.engine)
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
    freeCompilerArgs.addAll("-Xjsr305=strict")
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
