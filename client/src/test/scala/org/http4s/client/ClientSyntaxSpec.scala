package org.http4s
package client

import cats.effect._
import cats.effect.concurrent.Ref
import cats.implicits._
import fs2._
import org.http4s.Method._
import org.http4s.MediaType
import org.http4s.Status.{BadRequest, Created, InternalServerError, Ok}
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.headers.Accept
import org.http4s.Uri.uri
import org.specs2.matcher.MustThrownMatchers

class ClientSyntaxSpec extends Http4sSpec with Http4sClientDsl[IO] with MustThrownMatchers {

  val app = HttpRoutes
    .of[IO] {
      case r if r.method == GET && r.pathInfo == "/" =>
        Response[IO](Ok).withEntity("hello").pure[IO]
      case r if r.method == PUT && r.pathInfo == "/put" =>
        Response[IO](Created).withEntity(r.body).pure[IO]
      case r if r.method == GET && r.pathInfo == "/echoheaders" =>
        r.headers.get(Accept).fold(IO.pure(Response[IO](BadRequest))) { m =>
          Response[IO](Ok).withEntity(m.toString).pure[IO]
        }
      case r if r.pathInfo == "/status/500" =>
        Response[IO](InternalServerError).withEntity("Oops").pure[IO]
    }
    .orNotFound

  val client: Client[IO] = Client.fromHttpApp(app)

  val req: Request[IO] = Request(GET, uri("http://www.foo.bar/"))

  object SadTrombone extends Exception("sad trombone")

  def assertDisposes(f: Client[IO] => IO[Unit]) = {
    var disposed = false
    val dispose = IO {
      disposed = true
      ()
    }
    val disposingClient = Client { req: Request[IO] =>
      Resource.make(app(req))(_ => dispose)
    }
    f(disposingClient).attempt.unsafeRunSync()
    disposed must beTrue
  }

  "Client" should {
    "match responses to Uris with get" in {
      client.get(req.uri) {
        case Ok(_) => IO.pure("Ok")
        case _ => IO.pure("fail")
      } must returnValue("Ok")
    }

    "match responses to requests with fetch" in {
      client.fetch(req) {
        case Ok(_) => IO.pure("Ok")
        case _ => IO.pure("fail")
      } must returnValue("Ok")
    }

    "match responses to request tasks with fetch" in {
      client.fetch(IO.pure(req)) {
        case Ok(_) => IO.pure("Ok")
        case _ => IO.pure("fail")
      } must returnValue("Ok")
    }

    "match responses to request tasks with fetch" in {
      client.fetch(IO.pure(req)) {
        case Ok(_) => IO.pure("Ok")
        case _ => IO.pure("fail")
      } must returnValue("Ok")
    }

    "get disposes of the response on success" in {
      assertDisposes(_.get(req.uri) { _ =>
        IO.unit
      })
    }

    "get disposes of the response on failure" in {
      assertDisposes(_.get(req.uri) { _ =>
        IO.raiseError(SadTrombone)
      })
    }

    "get disposes of the response on uncaught exception" in {
      assertDisposes(_.get(req.uri) { _ =>
        sys.error("Don't do this at home, kids")
      })
    }

    "fetch disposes of the response on success" in {
      assertDisposes(_.fetch(req) { _ =>
        IO.unit
      })
    }

    "fetch disposes of the response on failure" in {
      assertDisposes(_.fetch(req) { _ =>
        IO.raiseError(SadTrombone)
      })
    }

    "fetch disposes of the response on uncaught exception" in {
      assertDisposes(_.fetch(req) { _ =>
        sys.error("Don't do this at home, kids")
      })
    }

    "fetch on task disposes of the response on success" in {
      assertDisposes(_.fetch(IO.pure(req)) { _ =>
        IO.unit
      })
    }

    "fetch on task disposes of the response on failure" in {
      assertDisposes(_.fetch(IO.pure(req)) { _ =>
        IO.raiseError(SadTrombone)
      })
    }

    "fetch on task disposes of the response on uncaught exception" in {
      assertDisposes(_.fetch(IO.pure(req)) { _ =>
        sys.error("Don't do this at home, kids")
      })
    }

    "fetch on task that does not match results in failed task" in {
      client.fetch(IO.pure(req))(PartialFunction.empty).attempt.unsafeRunSync() must beLeft {
        e: Throwable =>
          e must beAnInstanceOf[MatchError]
      }
    }

    "fetch Uris with expect" in {
      client.expect[String](req.uri) must returnValue("hello")
    }

    "fetch Uris with expectOr" in {
      client.expectOr[String](req.uri) { _ =>
        IO.pure(SadTrombone)
      } must returnValue("hello")
    }

    "fetch requests with expect" in {
      client.expect[String](req) must returnValue("hello")
    }

    "fetch requests with expectOr" in {
      client.expectOr[String](req) { _ =>
        IO.pure(SadTrombone)
      } must returnValue("hello")
    }

    "fetch request tasks with expect" in {
      client.expect[String](IO.pure(req)) must returnValue("hello")
    }

    "fetch request tasks with expectOr" in {
      client.expectOr[String](IO.pure(req)) { _ =>
        IO.pure(SadTrombone)
      } must returnValue("hello")
    }

    "status returns the status for a request" in {
      client.status(req) must returnValue(Status.Ok)
    }

    "status returns the status for a request task" in {
      client.status(IO.pure(req)) must returnValue(Status.Ok)
    }

    "successful returns the success of the status for a request" in {
      client.successful(req) must returnValue(true)
    }

    "successful returns the success of the status for a request task" in {
      client.successful(IO.pure(req)) must returnValue(true)
    }

    "status returns the status for a request" in {
      client.status(req) must returnValue(Status.Ok)
    }

    "status returns the status for a request task" in {
      client.status(IO.pure(req)) must returnValue(Status.Ok)
    }

    "successful returns the success of the status for a request" in {
      client.successful(req) must returnValue(true)
    }

    "successful returns the success of the status for a request task" in {
      client.successful(IO.pure(req)) must returnValue(true)
    }

    "return an unexpected status when expecting a URI returns unsuccessful status" in {
      client.expect[String](uri("http://www.foo.com/status/500")).attempt must returnValue(
        Left(UnexpectedStatus(Status.InternalServerError)))
    }

    "handle an unexpected status when calling a URI with expectOr" in {
      case class Boom(status: Status, body: String) extends Exception
      client
        .expectOr[String](uri("http://www.foo.com/status/500")) { resp =>
          resp.as[String].map(Boom(resp.status, _))
        }
        .attempt must returnValue(Left(Boom(InternalServerError, "Oops")))
    }

    "add Accept header on expect" in {
      client.expect[String](uri("http://www.foo.com/echoheaders")) must returnValue(
        "Accept: text/*")
    }

    "add Accept header on expect for requests" in {
      client.expect[String](Request[IO](GET, uri("http://www.foo.com/echoheaders"))) must returnValue(
        "Accept: text/*")
    }

    "add Accept header on expect for requests" in {
      client.expect[String](Request[IO](GET, uri("http://www.foo.com/echoheaders"))) must returnValue(
        "Accept: text/*")
    }

    "combine entity decoder media types correctly" in {
      // This is more of an EntityDecoder spec
      val edec =
        EntityDecoder.decodeBy[IO, String](MediaType.image.jpeg)(_ => DecodeResult.success("foo!"))
      client.expect(Request[IO](GET, uri("http://www.foo.com/echoheaders")))(
        EntityDecoder.text[IO].orElse(edec)) must returnValue("Accept: text/*, image/jpeg")
    }

    "return empty with expectOption and not found" in {
      client.expectOption[String](Request[IO](GET, uri("http://www.foo.com/random-not-found"))) must returnValue(
        Option.empty[String])
    }
    "return expected value with expectOption and a response" in {
      client.expectOption[String](Request[IO](GET, uri("http://www.foo.com/echoheaders"))) must returnValue(
        "Accept: text/*".some
      )
    }

    "stream returns a stream" in {
      client
        .stream(req)
        .flatMap(_.body.through(fs2.text.utf8Decode))
        .compile
        .toVector
        .unsafeRunSync() must_== Vector("hello")
    }

    "streaming disposes of the response on success" in {
      assertDisposes(_.stream(req).compile.drain)
    }

    "streaming disposes of the response on failure" in {
      assertDisposes(_.stream(req).flatMap(_ => Stream.raiseError[IO](SadTrombone)).compile.drain)
    }

    "toService disposes of the response on success" in {
      assertDisposes(_.toKleisli(_ => IO.unit).run(req))
    }

    "toService disposes of the response on failure" in {
      assertDisposes(_.toKleisli(_ => IO.raiseError(SadTrombone)).run(req))
    }

    "toHttpApp disposes the response if the body is run" in {
      assertDisposes(_.toHttpApp.flatMapF(_.body.compile.drain).run(req))
    }

    "toHttpApp disposes of the response if the body is run, even if it fails" in {
      assertDisposes(
        _.toHttpApp
          .flatMapF(_.body.flatMap(_ => Stream.raiseError[IO](SadTrombone)).compile.drain)
          .run(req))
    }

    "toHttpApp allows the response to be read" in {
      client.toHttpApp(req).flatMap(_.as[String]) must returnValue("hello")
    }

    "toHttpApp disposes of resources in reverse order of acquisition" in {
      Ref[IO].of(Vector.empty[Int]).flatMap { released =>
        Client[IO] { _ =>
          for {
            _ <- List(1, 2, 3).traverse { i =>
              Resource(IO.pure(() -> released.update(_ :+ i)))
            }
          } yield Response()
        }.toHttpApp(req).flatMap(_.as[Unit]) >> released.get
      } must returnValue(Vector(3, 2, 1))
    }

    "toHttpApp releases acquired resources on failure" in {
      Ref[IO].of(Vector.empty[Int]).flatMap { released =>
        Client[IO] { _ =>
          for {
            _ <- List(1, 2, 3).traverse { i =>
              Resource(IO.pure(() -> released.update(_ :+ i)))
            }
            _ <- Resource.liftF[IO, Unit](IO.raiseError(SadTrombone))
          } yield Response()
        }.toHttpApp(req).flatMap(_.as[Unit]).attempt >> released.get
      } must returnValue(Vector(3, 2, 1))
    }
  }

  "RequestResponseGenerator" should {
    "Generate requests based on Method" in {
      client.expect[String](GET(uri("http://www.foo.com/"))) must returnValue("hello")

      // The PUT: /put path just echoes the body
      client.expect[String](PUT("hello?", uri("http://www.foo.com/put"))) must returnValue("hello?")
    }
  }
}
