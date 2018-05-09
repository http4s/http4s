package org.http4s.client
package blaze

import cats.effect._
import cats.implicits._
import java.net.InetSocketAddress
import javax.servlet.ServletOutputStream
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.http4s._
import org.http4s.client.testroutes.GetRoutes
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Random

class PooledClientSpec extends Http4sSpec {

  private val timeout = 30.seconds

  private val failClient = Http1Client[IO](
    BlazeClientConfig.defaultConfig.copy(maxConnectionsPerRequestKey = _ => 0)).unsafeRunSync()
  private val successClient = Http1Client[IO](
    BlazeClientConfig.defaultConfig.copy(maxConnectionsPerRequestKey = _ => 1)).unsafeRunSync()
  private val client = Http1Client[IO](
    BlazeClientConfig.defaultConfig.copy(maxConnectionsPerRequestKey = _ => 3)).unsafeRunSync()

  private val failTimeClient =
    Http1Client[IO](
      BlazeClientConfig.defaultConfig
        .copy(maxConnectionsPerRequestKey = _ => 1, responseHeaderTimeout = 2 seconds))
      .unsafeRunSync()

  private val successTimeClient =
    Http1Client[IO](
      BlazeClientConfig.defaultConfig
        .copy(maxConnectionsPerRequestKey = _ => 1, responseHeaderTimeout = 20 seconds))
      .unsafeRunSync()

  private val drainTestClient =
    Http1Client[IO](
      BlazeClientConfig.defaultConfig
        .copy(maxConnectionsPerRequestKey = _ => 1, responseHeaderTimeout = 20 seconds))
      .unsafeRunSync()

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

          val writeBody: IO[Unit] = resp.body
            .evalMap { byte =>
              IO(os.write(Array(byte)))
            }
            .compile
            .drain
          val flushOutputStream: IO[Unit] = IO(os.flush())
          (writeBody *> IO.sleep(Random.nextInt(1000).millis) *> flushOutputStream)
            .unsafeRunSync()

        case None => srv.sendError(404)
      }
  }

  step {
    jettyServ.startServers(testServlet)
    addresses = jettyServ.addresses
  }

  "Blaze Http1Client" should {
    "raise error NoConnectionAllowedException if no connections are permitted for key" in {
      val u = uri("https://httpbin.org/get")
      val resp = failClient.expect[String](u).attempt.unsafeRunTimed(timeout)
      resp must_== Some(
        Left(NoConnectionAllowedException(RequestKey(u.scheme.get, u.authority.get))))
    }

    "make simple https requests" in {
      val resp =
        successClient.expect[String](uri("https://httpbin.org/get")).unsafeRunTimed(timeout)
      resp.map(_.length > 0) must beSome(true)
    }

    "behave and not deadlock" in {
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

    "obey request timeout" in {
      val address = addresses(0)
      val name = address.getHostName
      val port = address.getPort
      failTimeClient
        .expect[String](Uri.fromString(s"http://$name:$port/delayed").yolo)
        .attempt
        .unsafeToFuture()

      failTimeClient
        .expect[String](Uri.fromString(s"http://$name:$port/delayed").yolo)
        .attempt
        .unsafeToFuture()

      val resp = failTimeClient
        .expect[String](Uri.fromString(s"http://$name:$port/delayed").yolo)
        .attempt
        .map(_.right.exists(_.nonEmpty))
        .unsafeToFuture()
      Await.result(resp, 6 seconds) must beFalse
    }

    "unblock waiting connections" in {
      val address = addresses(0)
      val name = address.getHostName
      val port = address.getPort
      successTimeClient
        .expect[String](Uri.fromString(s"http://$name:$port/delayed").yolo)
        .attempt
        .unsafeToFuture()

      val resp = successTimeClient
        .expect[String](Uri.fromString(s"http://$name:$port/delayed").yolo)
        .attempt
        .map(_.right.exists(_.nonEmpty))
        .unsafeToFuture()
      Await.result(resp, 6 seconds) must beTrue
    }

    "drain waiting connections after shutdown" in {
      val address = addresses(0)
      val name = address.getHostName
      val port = address.getPort
      drainTestClient
        .expect[String](Uri.fromString(s"http://$name:$port/delayed").yolo)
        .attempt
        .unsafeToFuture()

      val resp = drainTestClient
        .expect[String](Uri.fromString(s"http://$name:$port/delayed").yolo)
        .attempt
        .map(_.right.exists(_.nonEmpty))
        .unsafeToFuture()

      (IO.sleep(100.millis) *> drainTestClient.shutdown).unsafeToFuture()

      Await.result(resp, 6.seconds) must beTrue
    }
  }

  step {
    failClient.shutdown.unsafeRunSync()
    successClient.shutdown.unsafeRunSync()
    failTimeClient.shutdown.unsafeRunSync()
    successTimeClient.shutdown.unsafeRunSync()
    drainTestClient.shutdown.unsafeRunSync()
    client.shutdown.unsafeRunSync()
    jettyServ.stopServers()
  }
}
