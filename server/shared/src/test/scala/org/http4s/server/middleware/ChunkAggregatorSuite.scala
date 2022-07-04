/*
 * Copyright 2014 http4s.org
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

package org.http4s.server.middleware

import cats.data.NonEmptyList
import cats.data.OptionT
import cats.effect.IO
import cats.syntax.all._
import fs2._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.headers._
import org.http4s.laws.discipline.arbitrary._
import org.http4s.testing.fs2Arbitraries._
import org.scalacheck.Gen._
import org.scalacheck._
import org.scalacheck.effect.PropF

class ChunkAggregatorSuite extends Http4sSuite {
  val transferCodingGen: Gen[List[TransferCoding]] =
    choose(0, 4).map(i =>
      List(
        TransferCoding.compress,
        TransferCoding.deflate,
        TransferCoding.gzip,
        TransferCoding.identity,
      ).take(i)
    )
  implicit val transferCodingArbitrary: Arbitrary[List[TransferCoding]] = Arbitrary(
    transferCodingGen
  )

  private def response(body: EntityBody[IO], transferCodings: List[TransferCoding]) =
    Ok(body, `Transfer-Encoding`(NonEmptyList(TransferCoding.chunked, transferCodings)))
      .map(_.removeHeader[`Content-Length`])

  def httpRoutes(body: EntityBody[IO], transferCodings: List[TransferCoding]): HttpRoutes[IO] =
    HttpRoutes.liftF(OptionT.liftF(response(body, transferCodings)))

  def httpApp(body: EntityBody[IO], transferCodings: List[TransferCoding]): HttpApp[IO] =
    HttpApp.liftF(response(body, transferCodings))

  def checkAppResponse(app: HttpApp[IO])(responseCheck: Response[IO] => IO[Boolean]): IO[Boolean] =
    ChunkAggregator.httpApp(app).run(Request()).flatMap { response =>
      responseCheck(response).map(_ && response.status === Ok)
    }

  def checkRoutesResponse(
      routes: HttpRoutes[IO]
  )(responseCheck: Response[IO] => IO[Boolean]): IO[Boolean] =
    ChunkAggregator.httpRoutes(routes).run(Request()).value.flatMap {
      case Some(response) => responseCheck(response).map(_ && response.status === Ok)
      case _ => false.pure[IO]
    }

  test("handle an empty body") {
    checkRoutesResponse(httpRoutes(EmptyBody, Nil)) { response =>
      response.body.compile.toVector.map(_.isEmpty && response.contentLength.isEmpty)
    }.assert
  }

  test("handle a none") {
    val routes: HttpRoutes[IO] = HttpRoutes.empty
    ChunkAggregator
      .httpRoutes(routes)
      .run(Request())
      .value
      .map(_ == Option.empty[Response[IO]])
      .assert
  }

  test("handle chunks") {
    PropF.forAllF { (chunks: NonEmptyList[Chunk[Byte]], transferCodings: List[TransferCoding]) =>
      val totalChunksSize = chunks.foldMap(_.size)
      val body = chunks.map(Stream.chunk).reduceLeft(_ ++ _)

      def check(response: Response[IO]) =
        response.body.compile.toVector.map {
          _ === chunks.foldMap(_.toVector) &&
            (if (totalChunksSize > 0) {
               response.contentLength === Some(totalChunksSize.toLong) &&
               response.headers.get[`Transfer-Encoding`].map(_.values) === NonEmptyList
                 .fromList(transferCodings)
             } else true)
        }

      (
        checkRoutesResponse(httpRoutes(body, transferCodings))(check),
        checkAppResponse(httpApp(body, transferCodings))(check),
      ).mapN(_ && _).assert
    }
  }
}
