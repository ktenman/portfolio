package ee.tenman.portfolio.job

import ch.tutteli.atrium.api.fluent.en_GB.notToContain
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
      jobClasses(poff = true).mapNotNull { type ->
        runCatching { processor.postProcessAfterInitialization(ObjenesisStd().newInstance(type), type.simpleName) }
          .exceptionOrNull()
          ?.let { "${type.simpleName}: $it" }
      }
    expect(failures).toBeEmpty()
  }

  @Test
  fun `should leave out the PÖFF jobs while PÖFF is off`() {
    expect(jobClasses(poff = false)).notToContain(PoffImportJob::class.java, ImdbRatingJob::class.java)
  }

  private fun jobClasses(poff: Boolean): List<Class<*>> =
    ClassPathScanningCandidateComponentProvider(false, MockEnvironment().withProperty("poff.enabled", poff.toString()))
      .apply { addIncludeFilter(AnnotationTypeFilter(Component::class.java)) }
      .findCandidateComponents(ScheduledJob::class.java.packageName)
      .map { Class.forName(it.beanClassName) }
}
