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

import cats.effect._
import io.circe.generic.auto._
import org.http4s.Uri
import org.http4s.Status.{NotFound, Successful}
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import scala.concurrent.ExecutionContext.global

object ClientExample extends IOApp {
  def getSite(client: Client[IO]): IO[Unit] =
    IO {
      val page: IO[String] = client.expect[String](Uri.uri("https://www.google.com/"))

      for (_ <- 1 to 2)
        println(
          page.map(_.take(72)).unsafeRunSync()
        ) // each execution of the effect will refetch the page!

      // We can do much more: how about decoding some JSON to a scala object
      // after matching based on the response status code?

      final case class Foo(bar: String)

      // Match on response code!
      val page2 = client.get(Uri.uri("http://http4s.org/resources/foo.json")) {
        case Successful(resp) =>
          // decodeJson is defined for Json4s, Argonuat, and Circe, just need the right decoder!
          resp.decodeJson[Foo].map("Received response: " + _)
        case NotFound(_) => IO.pure("Not Found!!!")
        case resp => IO.pure("Failed: " + resp.status)
      }

      println(page2.unsafeRunSync())
    }

  def run(args: List[String]): IO[ExitCode] =
    BlazeClientBuilder[IO](global).resource
      .use(getSite)
      .as(ExitCode.Success)
}
