package org.http4s.zipkin.core.algebras

import java.time.Instant

import scalaz.concurrent.Task

trait Clock {
  def now(): Instant
}

object Clock {
  def getInstant(clock: Clock): Task[Instant] = Task.delay {
    clock.now()
  }

}
