package org.http4s.ember.core

import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

private[ember] object Defaults {
  val idleTimeout: FiniteDuration = 60.seconds
}
