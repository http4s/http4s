package org.http4s.server.middleware

import org.http4s._
import org.http4s.dsl._
import org.http4s.headers.`Content-Length`
import org.http4s.util.CaseInsensitiveString
import org.specs2.matcher.MatchResult
import scodec.bits.ByteVector

import scalaz.stream.Cause.End
import scalaz.stream.Process
import scalaz.stream.Process.{Emit, Halt}

class ChunkAggregatorSpec extends Http4sSpec {

  "ChunkAggregator" should {
    "handle an empty body" in {
      checkResponse(EmptyBody) { response =>
        response.body.runLog.unsafePerformSync must_=== Vector.empty
      }
    }

    "handle a single chunk" in {
      val str = encodeUtf8String("hiya")
      checkResponse(Process.emit(str)) { response =>
        response.body.runLog.unsafePerformSync must_=== Vector(str)
      }
    }

    "handle multiple chunks" in {
      val strings = Seq("the", " quick", " brown", " fox").map(encodeUtf8String)
      checkResponse(Process.emitAll(strings)) { response =>
        //response.headers.get(CaseInsensitiveString("Content-Length")) must_== Some(`Content-Length`(19L))
        println(response.headers)
        response.body.runLog.unsafePerformSync must_=== Vector(encodeUtf8String("the quick brown fox"))
      }
    }
  }

  def checkResponse(body: EntityBody)(responseCheck: Response => MatchResult[Any]): MatchResult[Any] = {
    val service: HttpService = HttpService.lift { _ => Ok().withBody(body ++ Process.halt) }
    val response = ChunkAggregator(service).run(Request()).unsafePerformSync.orNotFound
    response.status must_== Ok
    responseCheck(response)
  }

  def encodeUtf8String(str: String): ByteVector = ByteVector.encodeUtf8(str).fold(throw _, identity)

}
