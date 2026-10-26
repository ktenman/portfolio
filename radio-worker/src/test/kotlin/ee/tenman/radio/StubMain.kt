package ee.tenman.radio

import kotlin.system.exitProcess

fun main(args: Array<String>) {
  exitProcess(launch(args.toList()) { _, _ -> StubEars(60.0) })
}
