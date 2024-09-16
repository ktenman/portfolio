plugins {
  id("org.springframework.boot") version "3.3.3"
  id("io.spring.dependency-management") version "1.1.6"
  kotlin("plugin.jpa") version "2.0.20"
  kotlin("jvm") version "2.0.20"
  kotlin("plugin.spring") version "2.0.20"
  id("jacoco")
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
}

extra["springCloudVersion"] = "2023.0.2"

val selenideVersion = "7.4.3"
val springRetryVersion = "2.0.9"
val guavaVersion = "33.3.0-jre"
val commonsMathVersion = "3.6.1"
val mokitoKotlinVersion = "5.4.0"
val coroutinesVersion = "1.9.0"

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.springframework.boot:spring-boot-starter-validation")
  implementation("org.springframework.boot:spring-boot-starter-data-redis")
  implementation("org.springframework.boot:spring-boot-starter-web")
  implementation("org.springframework.cloud:spring-cloud-starter-openfeign")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("com.codeborne:selenide:$selenideVersion")
  implementation("org.flywaydb:flyway-core")
  implementation("org.flywaydb:flyway-database-postgresql")
  implementation("org.jetbrains.kotlin:kotlin-reflect")
  implementation("org.springframework.retry:spring-retry:$springRetryVersion")
  implementation("com.google.guava:guava:$guavaVersion")
  implementation("org.apache.commons:commons-math3:$commonsMathVersion")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
  developmentOnly("org.springframework.boot:spring-boot-docker-compose")
  runtimeOnly("org.postgresql:postgresql")
  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("org.springframework.boot:spring-boot-testcontainers")
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
  testImplementation("org.springframework.cloud:spring-cloud-starter-contract-stub-runner")
  testImplementation("org.springframework.security:spring-security-test")
  testImplementation("org.testcontainers:junit-jupiter")
  testImplementation("org.junit.jupiter:junit-jupiter-api")
  testImplementation("org.testcontainers:postgresql")
  testImplementation("org.mockito.kotlin:mockito-kotlin:$mokitoKotlinVersion")
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
}

fun Test.configureE2ETestEnvironment() {
  include("**/e2e/**")
  val properties = mutableMapOf(
    "webdriver.chrome.logfile" to "build/reports/chromedriver.log",
    "webdriver.chrome.verboseLogging" to "true"
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
