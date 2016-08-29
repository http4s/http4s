package org.http4s.zipkin.algebras

import scalaz.concurrent.Task

trait Randomness {
  def nextLong(): Long
}

object Randomness {
  def getRandomLong(randomness: Randomness): Task[Long] = Task.delay {
    randomness.nextLong()
  }

}
