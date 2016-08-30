package org.http4s.zipkin.core.interpreters

import java.time.Instant

import org.http4s.zipkin.core.algebras.Clock

package object clock {
  val default = new Clock {
    override def now(): Instant = Instant.now()
  }

}
