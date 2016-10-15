package org.http4s
package util

import scalaz._
import scalaz.concurrent._
import scalaz.stream._
import scalaz.stream.Process._

trait ProcessApp {
  def main(args: List[String]): Process[Task, Unit]

  final def main(args: Array[String]): Unit = {
    val halt = async.signalOf(false)
    val halted = async.signalOf(false)

    val p = (halt.discrete wye main(args.toList))(wye.interrupt) append eval_(halted set true)

    sys.addShutdownHook {
      halt.set(true) runAsync { _ => () }
      halted.discrete.takeWhile(_ == false).run.run
    }

    p.run.attemptRun match {
      case -\/(t) =>
        t.printStackTrace()
        System.exit(-1)
      case \/-(_) =>
        ()
    }
  }
}
