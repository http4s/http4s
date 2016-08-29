package org.http4s.zipkin.interpreters

import java.time.Instant
import java.util.Random

import org.http4s.zipkin.algebras.Randomness

package object randomness {

  val default = apply(Instant.now.toEpochMilli)

  def apply(seed: Long) = new Randomness {
    val random = new Random(seed)
    override def nextLong(): Long = random.nextLong()
  }

}
