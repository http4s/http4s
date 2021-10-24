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
import org.http4s.Status.{NotFound, Successful}
import org.http4s.circe._
import org.http4s.syntax.all._
import org.http4s.client.Client
import org.http4s.blaze.client.BlazeClientBuilder

object ClientExample extends IOApp {
  def printGooglePage(client: Client[IO]): IO[Unit] = {
    val page: IO[String] = client.expect[String](uri"https://www.google.com/")
    IO.parSequenceN(2)((1 to 2).toList.map { _ =>
      for {
        // each execution of the effect will refetch the page!
        pageContent <- page
        firstBytes = pageContent.take(72)
        _ <- IO.println(firstBytes)
      } yield ()
    }).as(())
  }

  def matchOnResponseCode(client: Client[IO]): IO[Unit] = {
    final case class Foo(bar: String)

    for {
      // Match on response code!
      page <- client.get(uri"http://http4s.org/resources/foo.json") {
        case Successful(resp) =>
          // decodeJson is defined for Circe, just need the right decoder!
          resp.decodeJson[Foo].map("Received response: " + _)
        case NotFound(_) => IO.pure("Not Found!!!")
        case resp => IO.pure("Failed: " + resp.status)
      }
      _ <- IO.println(page)
    } yield ()
  }

  def getSite(client: Client[IO]): IO[Unit] =
    for {
      _ <- printGooglePage(client)
      // We can do much more: how about decoding some JSON to a scala object
      // after matching based on the response status code?
      _ <- matchOnResponseCode(client)
    } yield ()

  def run(args: List[String]): IO[ExitCode] =
    BlazeClientBuilder[IO].resource
      .use(getSite)
      .as(ExitCode.Success)
}
