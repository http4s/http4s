package org.http4s

import cats.effect.IO
import cats.effect.laws.util.TestContext
import org.scalacheck.Prop
import scala.util.Success

package object testing {
  // Media types used for testing
  val `text/css`: MediaType = new MediaType("text", "css", MimeDB.Compressible, MimeDB.NotBinary, List("css"))
  val `text/asp`: MediaType = new MediaType("text", "asp", MimeDB.Compressible, MimeDB.NotBinary, List("asp"))
  val `text/x-h` = new MediaType("text", "x-h")
  val `application/atom+xml`: MediaType = new MediaType("application", "atom+xml", true, false, List("atom"))
  val `application/excel`: MediaType = new MediaType("application", "excel", true, false, List("xls"))
  val `application/gnutar`: MediaType = new MediaType("application", "gnutar", true, false, List("tar"))
  val `audio/aiff`: MediaType = new MediaType("audio", "aiff", MimeDB.Compressible, MimeDB.Binary, List("aif", "aiff", "aifc"))
  val `application/soap+xml`: MediaType = new MediaType("application", "soap+xml", MimeDB.Compressible, MimeDB.NotBinary)
  val `application/vnd.ms-fontobject`: MediaType = new MediaType("application", "vnd.ms-fontobject", MimeDB.Compressible, MimeDB.Binary, List("eot"))
  val `image/jpeg`: MediaType = new MediaType("image", "jpeg", MimeDB.Uncompressible, MimeDB.Binary, List("jpeg", "jpg", "jpe"))
  val `audio/mod`: MediaType = new MediaType("audio", "mod", MimeDB.Uncompressible, MimeDB.Binary, List("mod"))
  val `audio/mpeg`: MediaType = new MediaType("audio", "mpeg", MimeDB.Uncompressible, MimeDB.Binary, List("mpga", "mp2", "mp2a", "mp3", "m2a", "m3a"))
  val `multipart/form-data`: MediaType = new MediaType("multipart", "form-data", MimeDB.Uncompressible, MimeDB.NotBinary)
  val `application/json`: MediaType = new MediaType("application", "json", MimeDB.Compressible, MimeDB.Binary, List("json", "map"))

  def ioBooleanToProp(iob: IO[Boolean])(implicit ec: TestContext): Prop = {
    val f = iob.unsafeToFuture()
    ec.tick()
    f.value match {
      case Some(Success(true)) => true
      case _ => false
    }
  }
}
