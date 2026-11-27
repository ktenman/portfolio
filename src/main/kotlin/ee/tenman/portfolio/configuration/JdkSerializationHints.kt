package ee.tenman.portfolio.configuration

import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.aot.hint.TypeReference
import java.io.Serializable
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path

class JdkSerializationHints : RuntimeHintsRegistrar {
  override fun registerHints(
    hints: RuntimeHints,
    classLoader: ClassLoader?,
  ) {
    PACKAGES.flatMap(::types).filter(::serializable).forEach { hints.serialization().registerType(TypeReference.of(it.name)) }
  }

  private fun types(packageName: String): List<Class<*>> {
    val directory = FileSystems.getFileSystem(URI.create("jrt:/")).getPath("modules", "java.base", *packageName.split('.').toTypedArray())
    return Files.list(directory).use { paths ->
      paths
        .map(Path::getFileName)
        .map(Path::toString)
        .filter { it.endsWith(".class") && !it.contains('-') }
        .map { Class.forName("$packageName.${it.removeSuffix(".class")}", false, null) }
        .toList()
    }
  }

  private fun serializable(type: Class<*>): Boolean = Serializable::class.java.isAssignableFrom(type) && !type.isInterface

  companion object {
    private val PACKAGES = listOf("java.lang", "java.math", "java.time", "java.util")
  }
}
