package com.example.http4s.blaze

import cats.effect.IO
import org.http4s.EntityEncoder._
import org.http4s.Uri._
import org.http4s._
import org.http4s.client._
import org.http4s.client.blaze.{defaultClient => client}
import org.http4s.dsl.io._
import org.http4s.headers._
import org.http4s.multipart._

object ClientMultipartPostExample {

  val bottle = getClass().getResource("/beerbottle.png")

  def go: String = {
    // n.b. This service does not appear to gracefully handle chunked requests.
    val url = Uri(
      scheme = Some("http".ci),
      authority = Some(Authority(host = RegName("www.posttestserver.com"))),
      path = "/post.php?dir=http4s")

    val multipart = Multipart[IO](
      Vector(
        Part.formData("text", "This is text."),
        Part.fileData("BALL", bottle, `Content-Type`(MediaType.`image/png`))
      ))

    val request: IO[Request[IO]] =
      Method.POST(url, multipart).map(_.replaceAllHeaders(multipart.headers))
    client[IO].expect[String](request).unsafeRunSync()
  }

  def main(args: Array[String]): Unit = println(go)
}
