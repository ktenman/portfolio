package ee.tenman.portfolio.domain

import jakarta.persistence.Column
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.Version
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.math.BigDecimal
import java.time.Instant

@MappedSuperclass
abstract class BaseEntity : Serializable {
  companion object {
    private const val serialVersionUID: Long = 1L
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  var id: Long = 0

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  lateinit var createdAt: Instant

  @UpdateTimestamp
  @Column(nullable = false)
  lateinit var updatedAt: Instant

  @Version
  @Column(nullable = false)
  var version: Long = 0
}

internal fun ObjectOutputStream.writeDecimal(value: BigDecimal?) = writeUTF(value?.toString().orEmpty())

internal fun ObjectInputStream.readDecimal(): BigDecimal = BigDecimal(readUTF())

internal fun ObjectInputStream.readOptionalDecimal(): BigDecimal? = readUTF().ifEmpty { null }?.let(::BigDecimal)
