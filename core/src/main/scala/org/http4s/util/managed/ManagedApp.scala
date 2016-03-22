package org.http4s.util.managed

import scalaz.{\/, Codensity, ImmutableArray}
import scalaz.concurrent.Task

trait ManagedApp {
  def run(args: Vector[String]): Managed[Unit]

  final def main(args: Array[String]): Unit =
    run(args.toVector).apply(Task.now).run
}
