plugins {
  id("org.springframework.boot") version "3.5.5"
  id("io.spring.dependency-management") version "1.1.7"
  kotlin("plugin.jpa") version "2.2.10"
  kotlin("jvm") version "2.2.10"
  kotlin("plugin.spring") version "2.2.10"
  id("jacoco")
  id("org.jlleitschuh.gradle.ktlint") version "13.1.0"
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

val springCloudVersion = "2025.0.0"
val springDocVersion = "2.8.10"
val ktlintVersion = "1.5.0"
val selenideVersion = "7.10.0"
val springRetryVersion = "2.0.12"
val resilience4jVersion = "2.3.0"
val rxjava3Version = "3.1.9"
val guavaVersion = "33.4.8-jre"
val commonsMathVersion = "3.6.1"
val jsoupVersion = "1.21.1"
val telegramBotsVersion = "6.9.7.1"
val googleCloudVisionVersion = "3.70.0"
val coroutinesVersion = "1.10.2"
val mockitoKotlinVersion = "6.0.0"
val kotestVersion = "6.0.1"
val archUnitVersion = "1.4.1"

extra["springCloudVersion"] = springCloudVersion

dependencies {
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springDocVersion")
  implementation("org.jsoup:jsoup:$jsoupVersion")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.springframework.boot:spring-boot-starter-validation")
  implementation("org.springframework.boot:spring-boot-starter-data-redis")
  implementation("org.springframework.boot:spring-boot-starter-web")
  implementation("org.springframework.cloud:spring-cloud-starter-openfeign")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

  // Security fixes for CVEs
  implementation("commons-fileupload:commons-fileupload:1.6.0")
  implementation("org.apache.commons:commons-lang3:3.18.0")
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.springframework.boot:spring-boot-starter-aop")
  implementation("io.micrometer:micrometer-registry-prometheus")
  implementation("com.codeborne:selenide:$selenideVersion")
  implementation("org.flywaydb:flyway-core")
  implementation("org.flywaydb:flyway-database-postgresql")
  implementation("org.jetbrains.kotlin:kotlin-reflect")
  implementation("org.springframework.retry:spring-retry:$springRetryVersion")
  implementation("io.github.resilience4j:resilience4j-circuitbreaker:$resilience4jVersion")
  implementation("io.github.resilience4j:resilience4j-retry:$resilience4jVersion")
  implementation("io.github.resilience4j:resilience4j-kotlin:$resilience4jVersion")
  implementation("com.google.guava:guava:$guavaVersion")
  implementation("org.apache.commons:commons-math3:$commonsMathVersion")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
  implementation("org.telegram:telegrambots:$telegramBotsVersion")
  implementation("org.telegram:telegrambots-spring-boot-starter:$telegramBotsVersion")

  implementation("com.google.cloud:google-cloud-vision:$googleCloudVisionVersion") {
    exclude(group = "commons-logging", module = "commons-logging")
  }

  developmentOnly("org.springframework.boot:spring-boot-docker-compose")
  runtimeOnly("org.postgresql:postgresql:42.7.7")
  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("org.springframework.boot:spring-boot-testcontainers")
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
  testImplementation("org.springframework.cloud:spring-cloud-starter-contract-stub-runner")
  testImplementation("org.testcontainers:junit-jupiter")
  testImplementation("org.junit.jupiter:junit-jupiter-api")
  testImplementation("org.testcontainers:postgresql")
  testImplementation("org.mockito.kotlin:mockito-kotlin:$mockitoKotlinVersion")
  testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
  testImplementation("io.kotest:kotest-property:$kotestVersion")
  testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
  testImplementation("com.tngtech.archunit:archunit-junit5:$archUnitVersion")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

dependencyManagement {
  imports {
    mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
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
  version.set(ktlintVersion)
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
