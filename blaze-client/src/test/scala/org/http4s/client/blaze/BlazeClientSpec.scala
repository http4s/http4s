package org.http4s.client
package blaze

import cats.effect._
import cats.effect.concurrent.{Deferred, Ref}
import cats.implicits._
import fs2.Stream
import java.util.concurrent.TimeoutException
import javax.net.ssl.SSLContext
import javax.servlet.ServletOutputStream
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.http4s._
import org.http4s.blaze.util.TickWheelExecutor
import org.http4s.client.ConnectionFailure
import org.http4s.client.testroutes.GetRoutes
import org.http4s.testing.Http4sLegacyMatchersIO
import org.specs2.specification.core.Fragments
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Random

class BlazeClientSpec extends Http4sSpec with Http4sLegacyMatchersIO {
  val tickWheel = new TickWheelExecutor(tick = 50.millis)

  /** the map method allows to "post-process" the fragments after their creation */
  override def map(fs: => Fragments) = super.map(fs) ^ step(tickWheel.shutdown())

  private val timeout = 30.seconds

  def mkClient(
      maxConnectionsPerRequestKey: Int,
      maxTotalConnections: Int = 5,
      responseHeaderTimeout: Duration = 30.seconds,
      requestTimeout: Duration = 45.seconds,
      chunkBufferMaxSize: Int = 1024,
      sslContextOption: Option[SSLContext] = Some(bits.TrustingSslContext)
  ) =
    BlazeClientBuilder[IO](testExecutionContext)
      .withSslContextOption(sslContextOption)
      .withCheckEndpointAuthentication(false)
      .withResponseHeaderTimeout(responseHeaderTimeout)
      .withRequestTimeout(requestTimeout)
      .withMaxTotalConnections(maxTotalConnections)
      .withMaxConnectionsPerRequestKey(Function.const(maxConnectionsPerRequestKey))
      .withChunkBufferMaxSize(chunkBufferMaxSize)
      .withScheduler(scheduler = tickWheel)
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
          (writeBody *> flushOutputStream).unsafeRunSync()

        case None => srv.sendError(404)
      }

    override def doPost(req: HttpServletRequest, resp: HttpServletResponse): Unit = {
      resp.setStatus(Status.Ok.code)
      req.getInputStream.close()
    }
  }

  "Blaze Http1Client" should {
    withResource(
      (
        JettyScaffold[IO](5, false, testServlet),
        JettyScaffold[IO](1, true, testServlet)
      ).tupled) {
      case (
          jettyServer,
          jettySslServer
          ) => {
        val addresses = jettyServer.addresses
        val sslAddress = jettySslServer.addresses.head

        "raise error NoConnectionAllowedException if no connections are permitted for key" in {
          val name = sslAddress.getHostName
          val port = sslAddress.getPort
          val u = Uri.fromString(s"https://$name:$port/simple").yolo
          val resp = mkClient(0).use(_.expect[String](u).attempt).unsafeRunTimed(timeout)
          resp must_== Some(
            Left(NoConnectionAllowedException(RequestKey(u.scheme.get, u.authority.get))))
        }

        "make simple https requests" in {
          val name = sslAddress.getHostName
          val port = sslAddress.getPort
          val u = Uri.fromString(s"https://$name:$port/simple").yolo
          val resp = mkClient(1).use(_.expect[String](u)).unsafeRunTimed(timeout)
          resp.map(_.length > 0) must beSome(true)
        }

        "reject https requests when no SSLContext is configured" in {
          val name = sslAddress.getHostName
          val port = sslAddress.getPort
          val u = Uri.fromString(s"https://$name:$port/simple").yolo
          val resp = mkClient(1, sslContextOption = None)
            .use(_.expect[String](u))
            .attempt
            .unsafeRunTimed(1.second)
          resp must beSome(beLeft[Throwable](beAnInstanceOf[ConnectionFailure]))
        }

        "behave and not deadlock" in {
          val hosts = addresses.map { address =>
            val name = address.getHostName
            val port = address.getPort
            Uri.fromString(s"http://$name:$port/simple").yolo
          }

          mkClient(3)
            .use { client =>
              (1 to Runtime.getRuntime.availableProcessors * 5).toList
                .parTraverse { _ =>
                  val h = hosts(Random.nextInt(hosts.length))
                  client.expect[String](h).map(_.nonEmpty)
                }
                .map(_.forall(identity))
            }
            .unsafeRunTimed(timeout) must beSome(true)
        }

        "behave and not deadlock on failures with parTraverse" in skipOnCi {
          mkClient(3)
            .use { client =>
              val failedHosts = addresses.map { address =>
                val name = address.getHostName
                val port = address.getPort
                Uri.fromString(s"http://$name:$port/internal-server-error").yolo
              }

              val successHosts = addresses.map { address =>
                val name = address.getHostName
                val port = address.getPort
                Uri.fromString(s"http://$name:$port/simple").yolo
              }

              val failedRequests =
                (1 to Runtime.getRuntime.availableProcessors * 5).toList.parTraverse { _ =>
                  val h = failedHosts(Random.nextInt(failedHosts.length))
                  client.expect[String](h)
                }

              val sucessRequests =
                (1 to Runtime.getRuntime.availableProcessors * 5).toList.parTraverse { _ =>
                  val h = successHosts(Random.nextInt(successHosts.length))
                  client.expect[String](h).map(_.nonEmpty)
                }

              val allRequests = for {
                _ <- failedRequests.handleErrorWith(_ => IO.unit).replicateA(5)
                r <- sucessRequests
              } yield r

              allRequests
                .map(_.forall(identity))
            }
            .unsafeRunTimed(timeout) must beSome(true)
        }

        "behave and not deadlock on failures with parSequence" in skipOnCi {
          mkClient(3)
            .use { client =>
              val failedHosts = addresses.map { address =>
                val name = address.getHostName
                val port = address.getPort
                Uri.fromString(s"http://$name:$port/internal-server-error").yolo
              }

              val successHosts = addresses.map { address =>
                val name = address.getHostName
                val port = address.getPort
                Uri.fromString(s"http://$name:$port/simple").yolo
              }

              val failedRequests = (1 to Runtime.getRuntime.availableProcessors * 5).toList.map {
                _ =>
                  val h = failedHosts(Random.nextInt(failedHosts.length))
                  client.expect[String](h)
              }.parSequence

              val sucessRequests = (1 to Runtime.getRuntime.availableProcessors * 5).toList.map {
                _ =>
                  val h = successHosts(Random.nextInt(successHosts.length))
                  client.expect[String](h).map(_.nonEmpty)
              }.parSequence

              val allRequests = for {
                _ <- failedRequests.handleErrorWith(_ => IO.unit).replicateA(5)
                r <- sucessRequests
              } yield r

              allRequests
                .map(_.forall(identity))
            }
            .unsafeRunTimed(timeout) must beSome(true)
        }

        "obey response header timeout" in {
          val address = addresses(0)
          val name = address.getHostName
          val port = address.getPort
          mkClient(1, responseHeaderTimeout = 100.millis)
            .use { client =>
              val submit = client.expect[String](Uri.fromString(s"http://$name:$port/delayed").yolo)
              submit
            }
            .unsafeRunSync() must throwA[TimeoutException]
        }

        "unblock waiting connections" in {
          val address = addresses(0)
          val name = address.getHostName
          val port = address.getPort
          mkClient(1, responseHeaderTimeout = 20.seconds)
            .use { client =>
              val submit = client.expect[String](Uri.fromString(s"http://$name:$port/delayed").yolo)
              for {
                _ <- submit.start
                r <- submit.attempt
              } yield r
            }
            .unsafeRunSync() must beRight
        }

        "reset request timeout" in skipOnCi {
          val address = addresses(0)
          val name = address.getHostName
          val port = address.getPort

          Ref[IO].of(0L).flatMap { _ =>
            mkClient(1, requestTimeout = 1.second).use { client =>
              val submit =
                client.status(Request[IO](uri = Uri.fromString(s"http://$name:$port/simple").yolo))
              submit *> timer.sleep(2.seconds) *> submit
            }
          } must returnValue(Status.Ok)
        }

        "drain waiting connections after shutdown" in {
          val address = addresses(0)
          val name = address.getHostName
          val port = address.getPort

          val resp = mkClient(1, responseHeaderTimeout = 20.seconds)
            .use { drainTestClient =>
              drainTestClient
                .expect[String](Uri.fromString(s"http://$name:$port/delayed").yolo)
                .attempt
                .start

              val resp = drainTestClient
                .expect[String](Uri.fromString(s"http://$name:$port/delayed").yolo)
                .attempt
                .map(_.exists(_.nonEmpty))
                .start

              // Wait 100 millis to shut down
              IO.sleep(100.millis) *> resp.flatMap(_.join)
            }
            .unsafeToFuture()

          Await.result(resp, 6.seconds) must beTrue
        }

        "cancel infinite request on completion" in {
          val address = addresses(0)
          val name = address.getHostName
          val port = address.getPort
          Deferred[IO, Unit]
            .flatMap { reqClosed =>
              mkClient(1, requestTimeout = 10.seconds).use { client =>
                val body = Stream(0.toByte).repeat.onFinalizeWeak(reqClosed.complete(()))
                val req = Request[IO](
                  method = Method.POST,
                  uri = Uri.fromString(s"http://$name:$port/").yolo
                ).withBodyStream(body)
                client.status(req) >> reqClosed.get
              }
            }
            .unsafeRunTimed(5.seconds) must beSome(())
        }

        "doesn't leak connection on timeout" in {
          val address = addresses.head
          val name = address.getHostName
          val port = address.getPort
          val uri = Uri.fromString(s"http://$name:$port/simple").yolo

          mkClient(1)
            .use { client =>
              val req = Request[IO](uri = uri)
              client
                .fetch(req) { _ =>
                  IO.never
                }
                .timeout(250.millis)
                .attempt >>
                client.status(req)
            }
            .unsafeRunTimed(5.seconds)
            .attempt must_== Some(Right(Status.Ok))
        }

        "call a second host after reusing connections on a first" in skipOnCi {
          // https://github.com/http4s/http4s/pull/2546
          mkClient(maxConnectionsPerRequestKey = Int.MaxValue, maxTotalConnections = 5)
            .use { client =>
              val uris = addresses.take(2).map { address =>
                val name = address.getHostName
                val port = address.getPort
                Uri.fromString(s"http://$name:$port/simple").yolo
              }
              val s = Stream(
                Stream.eval(
                  client.expect[String](Request[IO](uri = uris(0)))
                )).repeat.take(10).parJoinUnbounded ++ Stream.eval(
                client.expect[String](Request[IO](uri = uris(1))))
              s.compile.lastOrError
            }
            .unsafeRunTimed(5.seconds)
            .attempt must_== Some(Right("simple path"))
        }

        "raise a ConnectionFailure when a host can't be resolved" in {
          mkClient(1)
            .use { client =>
              client.status(Request[IO](uri = uri"http://example.invalid/"))
            }
            .attempt
            .unsafeRunSync() must beLike {
            case Left(e: ConnectionFailure) =>
              e.getMessage must_== "Error connecting to http://example.invalid using address example.invalid:80 (unresolved: true)"
          }
        }
      }
    }
  }
}
