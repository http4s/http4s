package org.http4s
package server.staticcontent

import scalaz.Kleisli.kleisli
import scalaz.concurrent.Task


class ResourceServiceSpec extends Http4sSpec with StaticContentShared {

  val s = resourceService(ResourceService.Config(""))
    .flatMapK(_.fold(Task.now(Response(Status.NotFound)))(Task.now))

  "ResourceService" should {

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
