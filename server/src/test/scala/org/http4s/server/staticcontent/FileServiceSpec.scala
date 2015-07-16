package org.http4s
package server
package staticcontent

import scalaz.concurrent.Task

class FileServiceSpec extends Http4sSpec with StaticContentShared {

  val s = fileService(FileService.Config(System.getProperty("user.dir")))
    .or(Task.now(Response(Status.NotFound)))

  "FileService" should {

    "Return a 200 Ok file" in {
      val req = Request(uri = uri("server/src/test/resources/testresource.txt"))
      val rb = runReq(req)

      rb._1 must_== testResource
      rb._2.status must_== Status.Ok
    }

    "Not find missing file" in {
      val req = Request(uri = uri("server/src/test/resources/testresource.txtt"))
      runReq(req)._2.status must equal (Status.NotFound)
    }

    "Return a 206 PartialContent file" in {
      val range = headers.Range(4)
      val req = Request(uri = uri("server/src/test/resources/testresource.txt")).withHeaders(range)
      val rb = runReq(req)

      rb._2.status must_== Status.PartialContent
      rb._1 must_== testResource.splitAt(4)._2
    }

    "Return a 206 PartialContent file" in {
      val range = headers.Range(-4)
      val req = Request(uri = uri("server/src/test/resources/testresource.txt")).withHeaders(range)
      val rb = runReq(req)

      rb._2.status must_== Status.PartialContent
      rb._1 must_== testResource.splitAt(testResource.size - 4)._2
      rb._1.size must_== 4
    }

    "Return a 206 PartialContent file" in {
      val range = headers.Range(2,4)
      val req = Request(uri = uri("server/src/test/resources/testresource.txt")).withHeaders(range)
      val rb = runReq(req)

      rb._2.status must_== Status.PartialContent
      rb._1 must_== testResource.slice(2, 4 + 1)  // the end number is inclusive in the Range header
      rb._1.size must_== 3
    }

    "Return a 200 OK on invalid range" in {
      val ranges = Seq(
                        headers.Range(2,-1),
                        headers.Range(2,1),
                        headers.Range(200),
                        headers.Range(200, 201),
                        headers.Range(-200)
                       )
      val reqs = ranges map (r => Request(uri = uri("server/src/test/resources/testresource.txt")).withHeaders(r))
      forall(reqs) { req =>
        val rb = runReq(req)
        rb._2.status must_== Status.Ok
        rb._1 must_== testResource
      }
    }
  }

}
