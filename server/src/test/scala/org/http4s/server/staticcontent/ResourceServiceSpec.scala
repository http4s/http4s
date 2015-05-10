package org.http4s
package server.staticcontent


class ResourceServiceSpec extends Http4sSpec with StaticContentShared {

  val s = resourceService(ResourceService.Config(""))

  "ResourceService" should {

    "Serve available content" in {
      val req = Request(uri = Uri.fromString("testresource.txt").yolo)
      val rb = runReq(req).get

      rb._1 must_== testResource
      rb._2.status must_== Status.Ok
    }

    "Generate non on missing content" in {
      val req = Request(uri = Uri.fromString("testresource.txtt").yolo)
      runReq(req) must_== None
    }
  }

}
