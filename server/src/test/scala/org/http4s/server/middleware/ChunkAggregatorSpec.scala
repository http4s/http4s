package org.http4s.server.middleware

import fs2._
import org.http4s._
import org.http4s.dsl._
import org.specs2.matcher.MatchResult

class ChunkAggregatorSpec extends Http4sSpec {

  "ChunkAggregator" should {
    "handle an empty body" in {
      checkResponse(EmptyBody) { response =>
        response.body.runLog.unsafeValue() must_=== Some(Vector.empty)
      }
    }

    "handle a Pass" in {
      val service: HttpService = HttpService.lift(_ => Pass.now)
      ChunkAggregator(service).run(Request()).unsafeValue() must beSome(Pass)
    }

    "handle a single chunk" in {
      val str = encodeUtf8String("hiya")
      checkResponse(Stream.emits(str)) { response =>
        response.contentLength must_=== Some(4L)
        response.body.runLog.unsafeValue() must_=== Some(str.toVector)
      }
    }

    "handle multiple chunks" in {
      val strings = Seq("the", " quick", " brown", " fox").map(str => Stream.emits[Task, Byte](encodeUtf8String(str)))
      val body = strings.tail.foldLeft(strings.head)(_ ++ _)
      checkResponse(body) { response =>
        response.contentLength must_=== Some(19L)
        response.body.runLog.unsafeValue() must_=== Some(encodeUtf8String("the quick brown fox").toVector)
      }
    }
  }

  def checkResponse(body: EntityBody)(responseCheck: Response => MatchResult[Any]): MatchResult[Any] = {
    val service: HttpService = HttpService.lift { _ => Ok().withBody(body) }
    ChunkAggregator(service).run(Request()).unsafeValue() must beSome.like {
      case maybeResponse: MaybeResponse =>
        val response = maybeResponse.orNotFound
        response.status must_== Ok
        responseCheck(response)
    }
  }

  def encodeUtf8String(str: String): Seq[Byte] = str.getBytes.toSeq

}
