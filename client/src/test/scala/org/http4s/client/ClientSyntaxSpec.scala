package org.http4s
package client

import cats.effect._
import fs2._
import fs2.Stream._
import org.http4s.Method._
import org.http4s.Status.{BadRequest, Created, InternalServerError, Ok}
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.headers.Accept
import org.specs2.matcher.MustThrownMatchers

class ClientSyntaxSpec extends Http4sSpec with Http4sClientDsl[IO] with MustThrownMatchers {

  val route = HttpService[IO] {
    case r if r.method == GET && r.pathInfo == "/" =>
      Response[IO](Ok).withBody("hello")
    case r if r.method == PUT && r.pathInfo == "/put" =>
      Response[IO](Created).withBody(r.body)
    case r if r.method == GET && r.pathInfo == "/echoheaders" =>
      r.headers.get(Accept).fold(IO.pure(Response[IO](BadRequest))) { m =>
        Response[IO](Ok).withBody(m.toString)
      }
    case r if r.pathInfo == "/status/500" =>
      Response(InternalServerError).withBody("Oops")
    case r => sys.error("Path not found: " + r.pathInfo)
  }

  val client: Client[IO] = Client.fromHttpService(route)

  val req: Request[IO] = Request(GET, uri("http://www.foo.bar/"))

  object SadTrombone extends Exception("sad trombone")

  def assertDisposes(f: Client[IO] => IO[Unit]) = {
    var disposed = false
    val dispose = IO {
      disposed = true
      ()
    }
    val disposingClient = Client(route.orNotFound.map(r => DisposableResponse(r, dispose)), IO.unit)
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
      val edec = EntityDecoder.decodeBy[IO, String](MediaType.`image/jpeg`)(_ =>
        DecodeResult.success("foo!"))
      client.expect(Request[IO](GET, uri("http://www.foo.com/echoheaders")))(
        EntityDecoder.text[IO].orElse(edec)) must returnValue("Accept: text/*, image/jpeg")
    }

    "streaming returns a stream" in {
      client
        .streaming(req)(_.body.through(fs2.text.utf8Decode))
        .compile
        .toVector
        .unsafeRunSync() must_== Vector("hello")
    }

    "streaming returns a stream from a request task" in {
      client
        .streaming(req)(_.body.through(fs2.text.utf8Decode))
        .compile
        .toVector
        .unsafeRunSync() must_== Vector("hello")
    }

    "streaming disposes of the response on success" in {
      assertDisposes(_.streaming(req)(_.body).compile.drain)
    }

    "streaming disposes of the response on failure" in {
      assertDisposes(_.streaming(req)(_ => Stream.raiseError(SadTrombone)).compile.drain)
    }

    "toService disposes of the response on success" in {
      assertDisposes(_.toKleisli(_ => IO.unit).run(req))
    }

    "toService disposes of the response on failure" in {
      assertDisposes(_.toKleisli(_ => IO.raiseError(SadTrombone)).run(req))
    }

    "toHttpService disposes the response if the body is run" in {
      assertDisposes(_.toHttpService.orNotFound.flatMapF(_.body.compile.drain).run(req))
    }

    "toHttpService disposes of the response if the body is run, even if it fails" in {
      assertDisposes(
        _.toHttpService.orNotFound
          .flatMapF(_.body.flatMap(_ => Stream.raiseError(SadTrombone)).compile.drain)
          .run(req))
    }

    "toHttpService allows the response to be read" in {
      client.toHttpService.orNotFound(req).flatMap(_.as[String]) must returnValue("hello")
    }

    "toHttpService allows the response to be read" in {
      client.toHttpService.orNotFound(req).flatMap(_.as[String]) must returnValue("hello")
    }
  }

  "RequestResponseGenerator" should {
    "Generate requests based on Method" in {
      client.expect[String](GET(uri("http://www.foo.com/"))) must returnValue("hello")

      // The PUT: /put path just echoes the body
      client.expect[String](PUT(uri("http://www.foo.com/put"), "hello?")) must returnValue("hello?")
    }
  }
}
