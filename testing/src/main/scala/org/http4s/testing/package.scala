package org.http4s

import cats.effect.IO
import cats.effect.laws.util.TestContext
import org.scalacheck.Prop
import scala.util.Success

package object testing {
  def ioBooleanToProp(iob: IO[Boolean])(implicit ec: TestContext): Prop = {
    val f = iob.unsafeToFuture()
    ec.tick()
    f.value match {
      case Some(Success(true)) => true
      case _ => false
    }
  }
}
