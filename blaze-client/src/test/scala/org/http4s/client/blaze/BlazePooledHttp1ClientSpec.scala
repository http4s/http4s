package org.http4s
package client
package blaze

import scalaz.concurrent.Task

class BlazePooledHttp1ClientSpec
  extends { val client = PooledHttp1Client() }
    with ClientRouteTestBattery("Blaze PooledHttp1Client", client) {

  val path = "/simple"

  def fetchBody = client.toService(_.as[String]).local { uri: Uri =>
    Request(uri = uri)
  }

  "PooledHttp1Client" should {
    "Repeat a simple request" in {
      val url = Uri.fromString(s"http://${address.getHostName}:${address.getPort}$path").yolo

      val f = (0 until 10).map(_ => Task.fork {
        val resp = fetchBody.run(url)
        resp.map(_.length)
      })

      foreach(Task.gatherUnordered(f).runFor(timeout)) { length =>
        length mustNotEqual 0
      }
    }
  }
}
