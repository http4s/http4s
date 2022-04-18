/*
 * Copyright 2013 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.http4s.blaze.demo.client

import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Resource
import com.example.http4s.blaze.demo.StreamUtils
import fs2.Stream
import org.http4s.Method._
import org.http4s._
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.headers.`Content-Type`
import org.http4s.implicits._
import org.http4s.multipart.Multiparts
import org.http4s.multipart.Part

import java.net.URL

object MultipartClient extends MultipartHttpClient

class MultipartHttpClient(implicit S: StreamUtils[IO]) extends IOApp with Http4sClientDsl[IO] {
  private val image: IO[URL] = IO.blocking(getClass.getResource("/beerbottle.png"))

  private def request(multiparts: Multiparts[IO]) =
    for {
      url <- image
      body <- multiparts.multipart(
        Vector(
          Part.formData("name", "gvolpe"),
          Part.fileData[IO]("rick", url, `Content-Type`(MediaType.image.png)),
        )
      )
    } yield POST(body, uri"http://localhost:8080/v1/multipart").withHeaders(body.headers)

  private val resources: Resource[IO, (Client[IO], Multiparts[IO])] =
    for {
      client <- BlazeClientBuilder[IO].resource
      multiparts <- Resource.eval(Multiparts.forSync[IO])
    } yield (client, multiparts)

  private val example =
    for {
      (client, multiparts) <- Stream.resource(resources)
      req <- Stream.eval(request(multiparts))
      value <- Stream.eval(client.expect[String](req))
      _ <- S.putStrLn(value)
    } yield ()

  override def run(args: List[String]): IO[ExitCode] =
    example.compile.drain.as(ExitCode.Success)
}
