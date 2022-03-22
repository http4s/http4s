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

package com.example.http4s.blaze

import cats.effect.Blocker
import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import org.http4s.Uri._
import org.http4s._
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.headers._
import org.http4s.multipart._
import org.http4s.syntax.literals._

import java.net.URL
import scala.concurrent.ExecutionContext.global

object ClientMultipartPostExample extends IOApp with Http4sClientDsl[IO] {
  private val blocker = Blocker.liftExecutionContext(global)

  private val bottle: URL = getClass.getResource("/beerbottle.png")

  def go(client: Client[IO], multiparts: Multiparts[IO]): IO[String] = {
    val url = Uri(
      scheme = Some(Scheme.http),
      authority = Some(Authority(host = RegName("httpbin.org"))),
      path = path"/post",
    )

    multiparts
      .multipart(
        Vector(
          Part.formData("text", "This is text."),
          Part.fileData("BALL", bottle, blocker, `Content-Type`(MediaType.image.png)),
        )
      )
      .flatMap { multipart =>
        val request: Request[IO] = Method.POST(multipart, url).withHeaders(multipart.headers)
        client.expect[String](request)
      }
  }

  def run(args: List[String]): IO[ExitCode] =
    Multiparts.forSync[IO].flatMap { multiparts =>
      BlazeClientBuilder[IO](global).resource
        .use(go(_, multiparts))
        .flatMap(s => IO(println(s)))
        .as(ExitCode.Success)
    }
}
