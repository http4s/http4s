package org.http4s
package server
package staticcontent

import org.http4s.server.middleware.URITranslation
import scodec.bits.ByteVector

import scalaz.concurrent.Task

class ResourceServiceSpec extends Http4sSpec with StaticContentShared {

  val s = resourceService(ResourceService.Config(""))

  "ResourceService" should {

    "Respect UriTranslation" in {
      val s2 = URITranslation.translateRoot("/foo")(s)

      def runReq(req: Request): (ByteVector, Response) = {
        val resp = s2.apply(req).run
        val body = resp.body.runLog.run.fold(ByteVector.empty)(_ ++ _)
        (body, resp)
      }

      {
        val req = Request(uri = uri("foo/testresource.txt"))
        val (bv,resp) = runReq(req)
        bv must_== testResource
        resp.status must_== Status.Ok
      }

      {
        val req = Request(uri = uri("testresource.txt"))
        val (_,resp) = runReq(req)
        resp.status must_== Status.NotFound
      }
    }

    "Serve available content" in {
      val req = Request(uri = Uri.fromString("testresource.txt").yolo)
      val rb = runReq(req)

      rb._1 must_== testResource
      rb._2.status must_== Status.Ok
    }

    "Generate non on missing content" in {
      val req = Request(uri = Uri.fromString("testresource.txtt").yolo)
      runReq(req)._2.status must equal (Status.NotFound)
    }
  }

}
