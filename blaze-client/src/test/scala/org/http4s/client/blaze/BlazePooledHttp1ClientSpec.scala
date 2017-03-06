package org.http4s
package client
package blaze

import org.http4s.client.testroutes.GetRoutes
import fs2.{Strategy, Task}

class BlazePooledHttp1ClientSpec
  extends { val client = PooledHttp1Client() }
    with ClientRouteTestBattery("Blaze PooledHttp1Client", client) {

  val path = GetRoutes.SimplePath

  def fetchBody = client.toService(_.as[String]).local { uri: Uri =>
    Request(uri = uri)
  }

  "PooledHttp1Client" should {
    "Repeat a simple request" in {
      val url = Uri.fromString(s"http://${address.getHostName}:${address.getPort}$path").yolo

      implicit val S: Strategy = Http4sSpec.TestPoolStrategy
      Task.parallelTraverse((0 until 10).toVector)(_ =>
        fetchBody.run(url).map(_.length)
      ).unsafeRunFor(timeout).forall(_ mustNotEqual 0)
    }
  }
}
