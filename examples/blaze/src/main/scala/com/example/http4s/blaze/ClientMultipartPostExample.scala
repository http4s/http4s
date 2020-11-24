/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.http4s.blaze

import cats.effect.{Blocker, ExitCode, IO, IOApp}
import java.net.URL
import org.http4s._
import org.http4s.Uri._
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.headers._
import org.http4s.multipart._
import scala.concurrent.ExecutionContext.global

object ClientMultipartPostExample extends IOApp with Http4sClientDsl[IO] {
  val blocker = Blocker.liftExecutionContext(global)

  val bottle: URL = getClass.getResource("/beerbottle.png")

  def go(client: Client[IO]): IO[String] = {
    // n.b. This service does not appear to gracefully handle chunked requests.
    val url = Uri(
      scheme = Some(Scheme.http),
      authority = Some(Authority(host = RegName("ptsv2.com"))),
      path = Uri.Path.fromString("/t/http4s/post"))

    val multipart = Multipart[IO](
      Vector(
        Part.formData("text", "This is text."),
        Part.fileData("BALL", bottle, blocker, `Content-Type`(MediaType.image.png))
      ))

    val request: Request[IO] =
      Method.POST(multipart, url).withHeaders(multipart.headers)

    client.expect[String](request)
  }

  def run(args: List[String]): IO[ExitCode] =
    BlazeClientBuilder[IO](global).resource
      .use(go)
      .flatMap(s => IO(println(s)))
      .as(ExitCode.Success)
}
