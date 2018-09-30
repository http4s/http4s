package org.http4s.server.middleware

import cats.data.{NonEmptyList, OptionT}
import cats.effect.IO
import cats.implicits._
import fs2._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.headers._
import org.scalacheck._
import org.specs2.matcher.MatchResult

class ChunkAggregatorSpec extends Http4sSpec {

  val transferCodingGen: Gen[Seq[TransferCoding]] =
    Gen.someOf(
      Seq(
        TransferCoding.compress,
        TransferCoding.deflate,
        TransferCoding.gzip,
        TransferCoding.identity))
  implicit val transferCodingArbitrary = Arbitrary(transferCodingGen.map(_.toList))

  "ChunkAggregator" should {
    def checkResponse(body: EntityBody[IO], transferCodings: List[TransferCoding])(
        responseCheck: Response[IO] => MatchResult[Any]): MatchResult[Any] = {
      val routes: HttpRoutes[IO] = HttpRoutes.liftF(
        OptionT.liftF(
          Ok(body, `Transfer-Encoding`(NonEmptyList(TransferCoding.chunked, transferCodings)))
            .map(_.removeHeader(`Content-Length`))))

      ChunkAggregator(OptionT.liftK[IO])(routes).run(Request()).value.unsafeRunSync must beSome
        .like {
          case response =>
            response.status must_== Ok
            responseCheck(response)
        }
    }

    "handle an empty body" in {
      checkResponse(EmptyBody, Nil) { response =>
        response.contentLength must beNone
        response.body.compile.toVector.unsafeRunSync() must_=== Vector.empty
      }
    }

    "handle a none" in {
      val routes: HttpRoutes[IO] = HttpRoutes.empty
      ChunkAggregator(OptionT.liftK[IO])(routes).run(Request()).value must returnValue(None)
    }

    "handle chunks" in {
      prop { (chunks: NonEmptyList[Chunk[Byte]], transferCodings: List[TransferCoding]) =>
        val totalChunksSize = chunks.foldMap(_.size)
        checkResponse(chunks.map(Stream.chunk).reduceLeft(_ ++ _), transferCodings) { response =>
          if (totalChunksSize > 0) {
            response.contentLength must beSome(totalChunksSize.toLong)
            response.headers.get(`Transfer-Encoding`).map(_.values) must_=== NonEmptyList
              .fromList(transferCodings)
          }
          response.body.compile.toVector.unsafeRunSync() must_=== chunks.foldMap(_.toVector)
        }
      }
    }
  }

}
