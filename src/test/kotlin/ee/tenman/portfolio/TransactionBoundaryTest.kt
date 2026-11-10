package ee.tenman.portfolio

import com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage
import com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.domain.JavaCodeUnit
import com.tngtech.archunit.core.domain.JavaMethod
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.transaction.annotation.Transactional

@AnalyzeClasses(packages = ["ee.tenman.portfolio"], importOptions = [ImportOption.DoNotIncludeTests::class])
class TransactionBoundaryTest {
  private val ownCode = resideInAPackage("ee.tenman.portfolio..")
  private val networkLibraries =
    resideInAnyPackage(
      "io.minio..",
      "java.net.http..",
      "org.springframework.web.client..",
      "org.telegram.telegrambots..",
    )

  @ArchTest
  fun transactionalCodeShouldNotCallExternalServices(classes: JavaClasses) {
    val violations =
      classes
        .flatMap { it.methods }
        .filter { runsInTransaction(it) }
        .flatMap { root -> externalCalls(root, listOf(name(root)), mutableSetOf()) }
    if (violations.isNotEmpty()) {
      throw AssertionError(
        "Network I/O inside a transaction holds a database connection for the whole call:\n" +
          violations.joinToString("\n") { "  - $it" },
      )
    }
  }

  private fun runsInTransaction(method: JavaMethod): Boolean =
    isTransactional(method) || method.referencesToSelf.any { handsLambdaToTransaction(it.origin) }

  private fun isTransactional(method: JavaMethod): Boolean =
    method.isAnnotatedWith(Transactional::class.java) || method.owner.isAnnotatedWith(Transactional::class.java)

  private fun handsLambdaToTransaction(origin: JavaCodeUnit): Boolean =
    origin.methodCallsFromSelf
      .mapNotNull { it.target.resolveMember().orElse(null) }
      .any { isTransactional(it) && it.rawParameterTypes.any { type -> type.name.startsWith("kotlin.jvm.functions.") } }

  private fun externalCalls(
    method: JavaMethod,
    path: List<String>,
    visited: MutableSet<JavaMethod>,
  ): List<String> {
    if (!visited.add(method)) return emptyList()
    val (external, own) = successors(method).partition { isExternal(it) }
    return report(path, external) + own.flatMap { externalCalls(it, path + name(it), visited) }
  }

  private fun report(
    path: List<String>,
    external: List<JavaMethod>,
  ): List<String> {
    if (external.isEmpty()) return emptyList()
    val targets = external.map { name(it) }.distinct().sorted()
    return listOf((path + targets.toString()).joinToString(" -> "))
  }

  private fun successors(method: JavaMethod): List<JavaMethod> {
    val called = method.methodCallsFromSelf.mapNotNull { it.target.resolveMember().orElse(null) }
    val referenced = method.methodReferencesFromSelf.mapNotNull { it.target.resolveMember().orElse(null) }
    return (called + referenced)
      .flatMap { listOf(it) + overrides(it) }
      .filter { isExternal(it) || ownCode.test(it.owner) }
  }

  private fun overrides(method: JavaMethod): List<JavaMethod> {
    if (!ownCode.test(method.owner)) return emptyList()
    return method.owner.allSubclasses.mapNotNull { subclass ->
      subclass.tryGetMethod(method.name, *method.rawParameterTypes.map { it.name }.toTypedArray()).orElse(null)
    }
  }

  private fun isExternal(method: JavaMethod): Boolean =
    method.owner.isAnnotatedWith(FeignClient::class.java) || networkLibraries.test(method.owner)

  private fun name(method: JavaMethod): String = "${method.owner.simpleName}.${method.name}"
}
