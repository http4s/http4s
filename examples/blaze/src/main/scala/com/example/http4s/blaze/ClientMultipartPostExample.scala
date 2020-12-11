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
