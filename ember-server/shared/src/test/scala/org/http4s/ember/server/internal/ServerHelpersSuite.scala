package org.http4s.ember.server.internal

import cats.data.Kleisli
import cats.effect.IO
import cats.effect.Ref
import cats.effect.Resource
import com.comcast.ip4s.Host
import com.comcast.ip4s.Port
import org.http4s.Http4sSuite
import org.http4s.HttpApp
import org.http4s.Request
import org.http4s.Response
import org.http4s.Status
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder

class ServerHelpersSuite extends Http4sSuite {

  test("requests include ConnectionInfo when socket addresses are available") {
    val host = Host.fromString("127.0.0.1").get

    def mkApp(ref: Ref[IO, Option[Request.Connection]]): HttpApp[IO] =
      Kleisli { (req: Request[IO]) =>
        ref
          .set(req.attributes.lookup(Request.Keys.ConnectionInfo))
          .flatMap(_ => IO.pure(Response[IO](Status.Ok)))
      }

    val program = (for {
      ref <- Resource.eval(Ref.of[IO, Option[Request.Connection]](None))
      server <- EmberServerBuilder
        .default[IO]
        .withHost(host)
        .withPort(Port.fromInt(0).get)
        .withHttpApp(mkApp(ref))
        .build
      client <- EmberClientBuilder.default[IO].build
    } yield (ref, server, client)).use { case (ref, server, client) =>
      val request = Request[IO](uri = server.baseUri)
      client.run(request).use(_.body.compile.drain) >>
        ref.get.flatMap { maybeConnection =>
          val expectedLocal = server.addressIp4s
          IO.fromOption(maybeConnection)(new RuntimeException("connection info missing"))
            .map { connection =>
              assertEquals(connection.local, expectedLocal)
              assertEquals(connection.secure, false)
            }
        }
    }

    program
  }
}
