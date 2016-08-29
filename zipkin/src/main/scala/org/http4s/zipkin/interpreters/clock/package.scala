package org.http4s.zipkin.interpreters

import java.time.Instant

import org.http4s.zipkin.algebras.Clock

package object clock {
  def default = new Clock {
    override def now(): Instant = Instant.now()
  }

}
