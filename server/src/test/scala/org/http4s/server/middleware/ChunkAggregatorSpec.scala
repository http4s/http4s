package org.http4s.server.middleware

import cats.data.NonEmptyList
import fs2._
import org.http4s._
import org.http4s.dsl._
import org.http4s.headers._
import org.specs2.matcher.MatchResult

class ChunkAggregatorSpec extends Http4sSpec {

  "ChunkAggregator" should {
    def checkResponse(body: EntityBody)(responseCheck: Response => MatchResult[Any]): MatchResult[Any] = {
      val service: HttpService = HttpService.lift { _ =>
        Ok().putHeaders(`Transfer-Encoding`(TransferCoding.chunked, TransferCoding.gzip)).withBody(body)
      }
      ChunkAggregator(service).run(Request()).unsafeValue() must beSome.like {
        case maybeResponse: MaybeResponse =>
          val response = maybeResponse.orNotFound
          response.status must_== Ok
          responseCheck(response)
      }
    }

    "handle an empty body" in {
      checkResponse(EmptyBody) { response =>
        response.contentLength must_=== None
        response.body.runLog.unsafeValue() must_=== Some(Vector.empty)
      }
    }

    "handle a Pass" in {
      val service: HttpService = HttpService.lift(_ => Pass.now)
      ChunkAggregator(service).run(Request()).unsafeValue() must beSome(Pass)
    }

    "handle chunks" in {
      prop { (chunks: NonEmptyList[Chunk[Byte]]) =>
        val totalChunksSize = chunks.foldMap(_.size)
        checkResponse(chunks.map(Stream.chunk[Task, Byte]).reduceLeft(_ ++ _)) { response =>
          if (totalChunksSize > 0) {
            response.contentLength must_=== Some(totalChunksSize.toLong)
            response.headers.get(`Transfer-Encoding`).map(_.hasChunked) must_=== Some(false)
          }
          response.body.runLog.unsafeValue() must beSome(chunks.foldMap(_.toVector))
        }
      }
    }
  }

}
