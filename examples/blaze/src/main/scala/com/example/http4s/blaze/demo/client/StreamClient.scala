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

import cats.effect.{Async, ExitCode, IO, IOApp}
import com.example.http4s.blaze.demo.StreamUtils
import io.circe.Json
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.{Request, Uri}
import org.typelevel.jawn.Facade

import scala.concurrent.ExecutionContext.Implicits.global

object StreamClient extends IOApp {
  def run(args: List[String]): IO[ExitCode] =
    new HttpClient[IO].run.as(ExitCode.Success)
}

class HttpClient[F[_]](implicit F: Async[F], S: StreamUtils[F]) {
  implicit val jsonFacade: Facade[Json] =
    new io.circe.jawn.CirceSupportParser(None, false).facade

  def run: F[Unit] =
    BlazeClientBuilder[F](global).stream
      .flatMap { client =>
        val request =
          Request[F](uri = Uri.unsafeFromString("http://localhost:8080/v1/dirs?depth=3"))
        for {
          response <- client.stream(request).flatMap(_.body.chunks.through(fs2.text.utf8.decodeC))
          _ <- S.putStr(response)
        } yield ()
      }
      .compile
      .drain
}
