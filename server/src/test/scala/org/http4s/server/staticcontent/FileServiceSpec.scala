package org.http4s
package server
package staticcontent

import fs2._
import org.http4s.Http4sSpec._
import org.http4s.server.middleware.URITranslation

class FileServiceSpec extends Http4sSpec with StaticContentShared {
  val s = fileService(FileService.Config(System.getProperty("user.dir"), executor = TestPool))

  "FileService" should {

    "Respect UriTranslation" in {
      val s2 = URITranslation.translateRoot("/foo")(s)

      {
        val req = Request(uri = uri("foo/server/src/test/resources/testresource.txt"))
        s2(req) must returnBody(testResource)
        s2(req) must returnStatus(Status.Ok)
      }

      {
        val req = Request(uri = uri("server/src/test/resources/testresource.txt"))
        s2(req) must returnStatus(Status.NotFound)
      }
    }

    "Return a 200 Ok file" in {
      val req = Request(uri = uri("server/src/test/resources/testresource.txt"))
      s(req) must returnBody(testResource)
      s(req) must returnStatus(Status.Ok)
    }

    "Not find missing file" in {
      val req = Request(uri = uri("server/src/test/resources/testresource.txtt"))
      s(req) must returnStatus(Status.NotFound)
    }

    "Return a 206 PartialContent file" in {
      val range = headers.Range(4)
      val req = Request(uri = uri("server/src/test/resources/testresource.txt")).replaceAllHeaders(range)

      s(req) must returnStatus(Status.PartialContent)
      s(req) must returnBody(Chunk.bytes(testResource.toArray.splitAt(4)._2))
    }

    "Return a 206 PartialContent file" in {
      val range = headers.Range(-4)
      val req = Request(uri = uri("server/src/test/resources/testresource.txt")).replaceAllHeaders(range)

      s(req) must returnStatus(Status.PartialContent)
      s(req) must returnBody(Chunk.bytes(testResource.toArray.splitAt(testResource.size - 4)._2))
    }

    "Return a 206 PartialContent file" in {
      val range = headers.Range(2,4)
      val req = Request(uri = uri("server/src/test/resources/testresource.txt")).replaceAllHeaders(range)

      s(req) must returnStatus(Status.PartialContent)
      s(req) must returnBody(Chunk.bytes(testResource.toArray.slice(2, 4 + 1))) // the end number is inclusive in the Range header
    }

    "Return a 200 OK on invalid range" in {
      val ranges = Seq(
                        headers.Range(2,-1),
                        headers.Range(2,1),
                        headers.Range(200),
                        headers.Range(200, 201),
                        headers.Range(-200)
                       )
      val reqs = ranges map (r => Request(uri = uri("server/src/test/resources/testresource.txt")).replaceAllHeaders(r))
      forall(reqs) { req =>
        s(req) must returnStatus(Status.Ok)
        s(req) must returnBody(testResource)
      }
    }
  }

}
