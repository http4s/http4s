/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.http4s.blaze.demo.client

import cats.effect.{Blocker, ExitCode, IO, IOApp, Resource}
import com.example.http4s.blaze.demo.StreamUtils
import fs2.Stream
import java.net.URL
import org.http4s._
import org.http4s.Method._
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.headers.`Content-Type`
import org.http4s.implicits._
import org.http4s.client.Client
import org.http4s.multipart.{Multipart, Part}
import scala.concurrent.ExecutionContext.global

object MultipartClient extends MultipartHttpClient

class MultipartHttpClient(implicit S: StreamUtils[IO]) extends IOApp with Http4sClientDsl[IO] {
  private val image: IO[URL] = IO(getClass.getResource("/beerbottle.png"))

  private def multipart(url: URL, blocker: Blocker) =
    Multipart[IO](
      Vector(
        Part.formData("name", "gvolpe"),
        Part.fileData("rick", url, blocker, `Content-Type`(MediaType.image.png))
      )
    )

  private def request(blocker: Blocker) =
    image
      .map(multipart(_, blocker))
      .map(body => POST(body, uri"http://localhost:8080/v1/multipart").withHeaders(body.headers))

  private val resources: Resource[IO, (Blocker, Client[IO])] =
    for {
      blocker <- Blocker[IO]
      client <- BlazeClientBuilder[IO](global).resource
    } yield (blocker, client)

  private val example =
    for {
      (blocker, client) <- Stream.resource(resources)
      req <- Stream.eval(request(blocker))
      value <- Stream.eval(client.expect[String](req))
      _ <- S.putStrLn(value)
    } yield ()

  override def run(args: List[String]): IO[ExitCode] =
    example.compile.drain.as(ExitCode.Success)
}
