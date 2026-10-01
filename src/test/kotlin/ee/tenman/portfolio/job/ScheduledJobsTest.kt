package ee.tenman.portfolio.job

import ch.tutteli.atrium.api.fluent.en_GB.toBeEmpty
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.type.filter.AnnotationTypeFilter
import org.springframework.mock.env.MockEnvironment
import org.springframework.objenesis.ObjenesisStd
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor
import org.springframework.stereotype.Component

class ScheduledJobsTest {
  @Test
  fun `should register every scheduled job method without errors`() {
    val processor = ScheduledAnnotationBeanPostProcessor().apply { setEmbeddedValueResolver(StandardEnvironment()::resolvePlaceholders) }
    val failures =
      jobClasses().mapNotNull { type ->
        runCatching { processor.postProcessAfterInitialization(ObjenesisStd().newInstance(type), type.simpleName) }
          .exceptionOrNull()
          ?.let { "${type.simpleName}: $it" }
      }
    expect(failures).toBeEmpty()
  }

  private fun jobClasses(): List<Class<*>> =
    ClassPathScanningCandidateComponentProvider(false, MockEnvironment())
      .apply { addIncludeFilter(AnnotationTypeFilter(Component::class.java)) }
      .findCandidateComponents(ScheduledJob::class.java.packageName)
      .map { Class.forName(it.beanClassName) }
}
