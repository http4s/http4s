package org.http4s

import cats.effect.IO
import cats.effect.laws.util.TestContext
import org.scalacheck.Prop
import scala.util.Success

package object testing {
  // Media types used for testing
  val `text/asp`: MediaType =
    new MediaType("text", "asp", MediaType.Compressible, MediaType.NotBinary, List("asp"))
  val `text/x-h` = new MediaType("text", "x-h")
  val `application/excel`: MediaType =
    new MediaType("application", "excel", true, false, List("xls"))
  val `application/gnutar`: MediaType =
    new MediaType("application", "gnutar", true, false, List("tar"))
  val `audio/aiff`: MediaType =
    new MediaType(
      "audio",
      "aiff",
      MediaType.Compressible,
      MediaType.Binary,
      List("aif", "aiff", "aifc"))
  val `application/soap+xml`: MediaType =
    new MediaType("application", "soap+xml", MediaType.Compressible, MediaType.NotBinary)
  val `audio/mod`: MediaType =
    new MediaType("audio", "mod", MediaType.Uncompressible, MediaType.Binary, List("mod"))

  def ioBooleanToProp(iob: IO[Boolean])(implicit ec: TestContext): Prop = {
    val f = iob.unsafeToFuture()
    ec.tick()
    f.value match {
      case Some(Success(true)) => true
      case _ => false
    }
  }
}
