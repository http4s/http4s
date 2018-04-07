package org.http4s

import cats.effect.IO
import cats.effect.laws.util.TestContext
import org.scalacheck.Prop
import scala.util.Success

package object testing {
  // Media types used for testing
  // Copied from the definitions on MimeDB
  val `text/css`: MediaType = MimeDB.text.`text/css`
  val `text/asp`: MediaType =
    new MediaType("text", "asp", MimeDB.Compressible, MimeDB.NotBinary, List("asp"))
  val `text/x-h` = new MediaType("text", "x-h")
  val `application/atom+xml`: MediaType = MimeDB.application.`application/atom+xml`
  val `application/excel`: MediaType =
    new MediaType("application", "excel", true, false, List("xls"))
  val `application/gnutar`: MediaType =
    new MediaType("application", "gnutar", true, false, List("tar"))
  val `audio/aiff`: MediaType =
    new MediaType("audio", "aiff", MimeDB.Compressible, MimeDB.Binary, List("aif", "aiff", "aifc"))
  val `application/soap+xml`: MediaType =
    new MediaType("application", "soap+xml", MimeDB.Compressible, MimeDB.NotBinary)
  val `application/vnd.ms-fontobject`: MediaType =
    MimeDB.application.`application/vnd.ms-fontobject`
  val `image/jpeg`: MediaType = MimeDB.image.`image/jpeg`
  new MediaType("image", "jpeg", MimeDB.Uncompressible, MimeDB.Binary, List("jpeg", "jpg", "jpe"))
  val `audio/mod`: MediaType =
    new MediaType("audio", "mod", MimeDB.Uncompressible, MimeDB.Binary, List("mod"))
  val `audio/mpeg`: MediaType = MimeDB.audio.`audio/mpeg`
  val `multipart/form-data`: MediaType = MimeDB.multipart.`multipart/form-data`

  def ioBooleanToProp(iob: IO[Boolean])(implicit ec: TestContext): Prop = {
    val f = iob.unsafeToFuture()
    ec.tick()
    f.value match {
      case Some(Success(true)) => true
      case _ => false
    }
  }
}
