package org.http4s.client.blaze

import fs2.{Strategy, Task}

import org.http4s._
import org.http4s.client.{Client, ClientRouteTestBattery}
import org.http4s.client.testroutes.GetRoutes
import org.specs2.specification.core.Fragments

import scala.concurrent.duration._

class BlazePooledHttp1ClientRecycleSpec(client: Client)
extends ClientRouteTestBattery("Blaze PooledHttp1Client - recycling", client) with GetRoutes {
  def this() = this(PooledHttp1Client())

  override def runAllTests(): Fragments = {
    val address = initializeServer()
    val path = "/simple"
    val url = Uri.fromString(s"http://${address.getHostName}:${address.getPort}$path").yolo

    Fragments().append {
      "RecyclingHttp1Client" should {
        def fetchBody = client.toService(_.as[String]).local { uri: Uri => Request(uri = uri) }

        "Use supported path" in {
          getPaths.contains(path) must beTrue
        }

        "Make simple requests" in {
          val resp = fetchBody.run(url).unsafeRunFor(timeout)
          resp.length mustNotEqual 0
        }

        "Repeat a simple request" in {
          implicit val S: Strategy = Http4sSpec.TestPoolStrategy
          Task.parallelTraverse((0 until 10).toVector)(_ =>
            fetchBody.run(url).map(_.length)
          ).unsafeRunFor(timeout).forall(_ mustNotEqual 0)
        }
      }
    }
  }
}
