package org.http4s.client.blaze

import java.net.InetSocketAddress
import javax.servlet.ServletOutputStream
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import cats.effect._
import cats.implicits._
import org.http4s._
import scala.concurrent.duration._
import scala.util.Random
import org.http4s.client.testroutes.GetRoutes
import org.http4s.client.{Client, JettyScaffold, RequestKey}

class MaxConnectionsInPoolSpec extends Http4sSpec {

  private val timeout = 30.seconds

  private def mkClient(f: RequestKey => Int): Client[IO] =
    Http1Client[IO](BlazeClientConfig.defaultConfig.copy(maxConnectionsPerRequestKey = f)).unsafeRunSync

  private val failClient = mkClient(_ => 0)
  private val successClient = mkClient(_ => 1)
  private val client = mkClient(_ => 3)

  val jettyServ = new JettyScaffold(5)
  var addresses = Vector.empty[InetSocketAddress]

  private def testServlet = new HttpServlet {
    override def doGet(req: HttpServletRequest, srv: HttpServletResponse): Unit =
      GetRoutes.getPaths.get(req.getRequestURI) match {
        case Some(resp) =>
          srv.setStatus(resp.status.code)
          resp.headers.foreach { h =>
            srv.addHeader(h.name.toString, h.value)
          }

          val os: ServletOutputStream = srv.getOutputStream

          val writeBody: IO[Unit] = resp.body.evalMap { byte =>
            IO(os.write(Array(byte)))
          }.run
          val flushOutputStream: IO[Unit] = IO(os.flush())
          (writeBody *> IO(Thread.sleep(Random.nextInt(1000).toLong)) *> flushOutputStream)
            .unsafeRunSync()

        case None => srv.sendError(404)
      }
  }

  step {
    jettyServ.startServers(testServlet)
    addresses = jettyServ.addresses
  }

  "Blaze Pooled Http1 Client with zero max connections" should {
    "Not make simple https requests" in {
      val resp = failClient.expect[String](uri("https://httpbin.org/get")).attempt.unsafeRunSync()

      resp must beLeft
    }
  }

  "Blaze Pooled Http1 Client" should {
    "Make simple https requests" in {
      val resp =
        successClient.expect[String](uri("https://httpbin.org/get")).unsafeRunTimed(timeout)
      resp.map(_.length > 0) must beSome(true)
    }
  }

  "Blaze Pooled Http1 Client" should {
    "Behave and not deadlock" in {
      val hosts = addresses.map { address =>
        val name = address.getHostName
        val port = address.getPort
        Uri.fromString(s"http://$name:$port/simple").yolo
      }

      (0 until 42)
        .map { _ =>
          val h = hosts(Random.nextInt(hosts.length))
          val resp =
            client.expect[String](h).unsafeRunTimed(timeout)
          resp.map(_.length > 0)
        }
        .forall(_.contains(true)) must beTrue
    }
  }

  step {
    failClient.shutdown.unsafeRunSync()
    successClient.shutdown.unsafeRunSync()
    client.shutdown.unsafeRunSync()
    jettyServ.stopServers()
  }
}
