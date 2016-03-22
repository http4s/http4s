package org.http4s.util

import scalaz.{\/, Codensity, ImmutableArray}
import scalaz.concurrent.Task

trait ManagedApp {
  def run(args: Vector[String]): Codensity[Task, Unit]

  final def main(args: Array[String]): Unit =
    run(args.toVector).apply(Task.now).run
}
