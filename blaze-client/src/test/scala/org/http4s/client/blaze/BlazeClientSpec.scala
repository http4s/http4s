package org.http4s.client
package blaze

import cats.effect._
import cats.effect.concurrent.{Deferred, Ref}
import cats.implicits._
import fs2.Stream
import java.util.concurrent.TimeoutException
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
      requestTimeout: Duration = 1.minute,
      chunkBufferMaxSize: Int = 1024
  ) =
    BlazeClientBuilder[IO](testExecutionContext)
      .withSslContext(bits.TrustingSslContext)
      .withCheckEndpointAuthentication(false)
      .withResponseHeaderTimeout(responseHeaderTimeout)
      .withRequestTimeout(requestTimeout)
      .withMaxConnectionsPerRequestKey(Function.const(maxConnectionsPerRequestKey))
      .withChunkBufferMaxSize(chunkBufferMaxSize)
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
    // This incident is going on my permanent record.
    withResource(
      (
        mkClient(0),
        mkClient(1),
        mkClient(3),
        mkClient(3),
        mkClient(3),
        mkClient(1, 20.seconds),
        JettyScaffold[IO](5, false, testServlet),
        JettyScaffold[IO](1, true, testServlet)
      ).tupled) {
      case (
          failClient,
          successClient,
          client,
          seqClient,
          parClient,
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

          (1 to Runtime.getRuntime.availableProcessors * 5).toList
            .parTraverse { _ =>
              val h = hosts(Random.nextInt(hosts.length))
              client.expect[String](h).map(_.nonEmpty)
            }
            .map(_.forall(identity))
            .unsafeRunTimed(timeout) must beSome(true)
        }

        "behave and not deadlock on failures with parTraverse" in {
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
              parClient.expect[String](h)
            }

          val sucessRequests =
            (1 to Runtime.getRuntime.availableProcessors * 5).toList.parTraverse { _ =>
              val h = successHosts(Random.nextInt(successHosts.length))
              parClient.expect[String](h).map(_.nonEmpty)
            }

          val allRequests = for {
            _ <- failedRequests.handleErrorWith(_ => IO.unit).replicateA(5)
            r <- sucessRequests
          } yield r

          allRequests
            .map(_.forall(identity))
            .unsafeRunTimed(timeout) must beSome(true)
        }

        "behave and not deadlock on failures with parSequence" in {
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

          val failedRequests = (1 to Runtime.getRuntime.availableProcessors * 5).toList.map { _ =>
            val h = failedHosts(Random.nextInt(failedHosts.length))
            seqClient.expect[String](h)
          }.parSequence

          val sucessRequests = (1 to Runtime.getRuntime.availableProcessors * 5).toList.map { _ =>
            val h = successHosts(Random.nextInt(successHosts.length))
            seqClient.expect[String](h).map(_.nonEmpty)
          }.parSequence

          val allRequests = for {
            _ <- failedRequests.handleErrorWith(_ => IO.unit).replicateA(5)
            r <- sucessRequests
          } yield r

          allRequests
            .map(_.forall(identity))
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
          mkClient(1)
            .use { client =>
              val submit = successTimeClient
                .expect[String](Uri.fromString(s"http://$name:$port/delayed").yolo)
              for {
                _ <- submit.start
                r <- submit.attempt
              } yield r
            }
            .unsafeRunSync() must beRight
        }

        "reset request timeout" in {
          val address = addresses(0)
          val name = address.getHostName
          val port = address.getPort

          Ref[IO].of(0L).flatMap { nanos =>
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

        "cancel infinite request on completion" in {
          val address = addresses(0)
          val name = address.getHostName
          val port = address.getPort
          Deferred[IO, Unit]
            .flatMap { reqClosed =>
              mkClient(1, requestTimeout = 10.seconds).use { client =>
                val body = Stream(0.toByte).repeat.onFinalize(reqClosed.complete(()))
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
      }
    }
  }
}
