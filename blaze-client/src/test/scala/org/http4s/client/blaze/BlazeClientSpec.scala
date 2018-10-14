package org.http4s.client
package blaze

import cats.effect._
import cats.effect.concurrent.Ref
import cats.implicits._
import java.util.concurrent.TimeUnit
import javax.servlet.ServletOutputStream
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.http4s._
import org.http4s.client.testroutes.GetRoutes
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Random

class BlazeClientSpec extends Http4sSpec {

  private val timeout = 30.seconds

  def mkClient(
      maxConnectionsPerRequestKey: Int,
      responseHeaderTimeout: Duration = 1.minute,
      requestTimeout: Duration = 1.minute
  )(implicit timer: Timer[IO]) =
    BlazeClientBuilder[IO](testExecutionContext)
      .withSslContext(bits.TrustingSslContext)
      .withCheckEndpointAuthentication(false)
      .withResponseHeaderTimeout(responseHeaderTimeout)
      .withRequestTimeout(requestTimeout)
      .withMaxConnectionsPerRequestKey(Function.const(maxConnectionsPerRequestKey))
      .resource

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

  "Blaze Http1Client" should {
    // This incident is going on my permanent record.
    withResource(
      (
        mkClient(0),
        mkClient(1),
        mkClient(3),
        mkClient(1, 2.seconds),
        mkClient(1, 20.seconds),
        JettyScaffold[IO](5, false, testServlet),
        JettyScaffold[IO](1, true, testServlet)
      ).tupled) {
      case (
          failClient,
          successClient,
          client,
          failTimeClient,
          successTimeClient,
          jettyServer,
          jettySslServer
          ) => {
        val addresses = jettyServer.addresses
        val sslAddress = jettySslServer.addresses.head

        "raise error NoConnectionAllowedException if no connections are permitted for key" in {
          val name = sslAddress.getHostName
          val port = sslAddress.getPort
          val u = Uri.fromString(s"https://$name:$port/simple").yolo
          val resp = failClient.expect[String](u).attempt.unsafeRunTimed(timeout)
          resp must_== Some(
            Left(NoConnectionAllowedException(RequestKey(u.scheme.get, u.authority.get))))
        }

        "make simple https requests" in {
          val name = sslAddress.getHostName
          val port = sslAddress.getPort
          val u = Uri.fromString(s"https://$name:$port/simple").yolo
          val resp = successClient.expect[String](u).unsafeRunTimed(timeout)
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

        "obey response line timeout" in {
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

        "reset request timeout" in {
          val address = addresses(0)
          val name = address.getHostName
          val port = address.getPort

          Ref[IO].of(0L).flatMap { nanos =>
            implicit val testTimer: Timer[IO] = new Timer[IO] {
              val clock = new Clock[IO] {
                def monotonic(unit: TimeUnit) =
                  nanos.get.map(unit.convert(_, TimeUnit.NANOSECONDS))
                def realTime(unit: TimeUnit) =
                  monotonic(unit)
              }
              def sleep(duration: FiniteDuration) = timer.sleep(duration)
            }

            mkClient(1, requestTimeout = 1.second)(testTimer).use { client =>
              val submit =
                client.status(Request[IO](uri = Uri.fromString(s"http://$name:$port/simple").yolo))
              submit *> nanos.update(_ + 2.seconds.toNanos) *> submit
            }
          } must returnValue(Status.Ok)
        }

        "drain waiting connections after shutdown" in {
          val address = addresses(0)
          val name = address.getHostName
          val port = address.getPort

          val resp = mkClient(1, 20.seconds)
            .use { drainTestClient =>
              drainTestClient
                .expect[String](Uri.fromString(s"http://$name:$port/delayed").yolo)
                .attempt
                .start

              val resp = drainTestClient
                .expect[String](Uri.fromString(s"http://$name:$port/delayed").yolo)
                .attempt
                .map(_.right.exists(_.nonEmpty))
                .start

              // Wait 100 millis to shut down
              IO.sleep(100.millis) *> resp.flatMap(_.join)
            }
            .unsafeToFuture()

          Await.result(resp, 6.seconds) must beTrue
        }
      }
    }
  }
}
