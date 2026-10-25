import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
  kotlin("jvm")
  application
  id("org.jlleitschuh.gradle.ktlint")
  id("dev.detekt")
}

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(25)
  }
}

repositories {
  mavenCentral()
  exclusiveContent {
    forRepository {
      ivy {
        url = uri("https://github.com/k2-fsa/sherpa-onnx/releases/download")
        patternLayout { artifact("v[revision]/[artifact]-[revision].[ext]") }
        metadataSources { artifact() }
      }
    }
    filter { includeGroup("com.k2fsa") }
  }
}

dependencies {
  implementation(platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.springBoot.get()}"))
  implementation(libs.jackson.module.kotlin)
  implementation(libs.kotlin.reflect)
  implementation(libs.sherpa.onnx.jvm)
  runtimeOnly(libs.sherpa.onnx.native.linux.x64)
  runtimeOnly(libs.sherpa.onnx.native.linux.aarch64)
  runtimeOnly(libs.sherpa.onnx.native.osx.aarch64)
  testImplementation(libs.junit.jupiter.api)
  testImplementation(libs.atrium.fluent)
  testRuntimeOnly(libs.junit.jupiter.engine)
  testRuntimeOnly(libs.junit.platform.launcher)
}

application {
  mainClass = "ee.tenman.radio.MainKt"
  applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

tasks.test {
  useJUnitPlatform()
  jvmArgs("--enable-native-access=ALL-UNNAMED")
  systemProperty("junit.jupiter.execution.timeout.default", "2m")
  filter.isFailOnNoMatchingTests = false
  inputs.dir("ced-tiny")
  jvmArgumentProviders.add(CommandLineArgumentProvider { listOf("-Dradio.classpath=${sourceSets.test.get().runtimeClasspath.asPath}") })
}

configure<KtlintExtension> {
  version.set(libs.versions.ktlint.get())
}

detekt {
  buildUponDefaultConfig = true
  allRules = false
  config.setFrom(rootProject.files("detekt.yml"))
}

configurations.matching { it.name == "detekt" || it.name == "ktlint" }.configureEach {
  resolutionStrategy.eachDependency {
    if (requested.group == "org.jetbrains.kotlin") requested.version?.let { useVersion(it) }
  }
}
