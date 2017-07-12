package org.http4s
package client

import org.http4s.Http4sSpec
import org.http4s.headers.Accept
import org.http4s.Status.InternalServerError
import fs2._
import fs2.Stream._
import fs2.interop.cats._
import org.http4s.Status.{Ok, NotFound, Created, BadRequest}
import org.http4s.Method._

import org.specs2.matcher.MustThrownMatchers

class ClientSyntaxSpec extends Http4sSpec with MustThrownMatchers {

  val route = HttpService {
    case r if r.method == GET && r.pathInfo == "/"            =>
      Response(Ok).withBody("hello")
    case r if r.method == PUT && r.pathInfo == "/put"         =>
      Response(Created).withBody(r.body)
    case r if r.method == GET && r.pathInfo == "/echoheaders" =>
      r.headers.get(Accept).fold(Task.now(Response(BadRequest))){ m =>
         Response(Ok).withBody(m.toString)
      }
    case r if r.pathInfo == "/status/500" =>
      Response(InternalServerError).withBody("Oops")
    case r => sys.error("Path not found: " + r.pathInfo)
  }

  val client = Client.fromHttpService(route)

  val req = Request(GET, uri("http://www.foo.bar/"))

  object SadTrombone extends Exception("sad trombone")



  def assertDisposes(f: Client => Task[Unit]) = {
    var disposed = false
    val disposingClient = Client(
      route.map(r => DisposableResponse(r.orNotFound, Task.delay(disposed = true))),
      Task.now(()))
    f(disposingClient).unsafeAttemptValue()
    disposed must beTrue
  }

  "Client" should {
    "match responses to Uris with get" in {
      client.get(req.uri) {
        case Ok(resp) => Task.now("Ok")
        case _ => Task.now("fail")
      } must returnValue("Ok")
    }

    "match responses to requests with fetch" in {
      client.fetch(req) {
        case Ok(resp) => Task.now("Ok")
        case _ => Task.now("fail")
      } must returnValue("Ok")
    }

    "match responses to request tasks with fetch" in {
      client.fetch(Task.now(req)) {
        case Ok(resp) => Task.now("Ok")
        case _ => Task.now("fail")
      } must returnValue("Ok")
    }

    "match responses to request tasks with fetch" in {
      client.fetch(Task.now(req)) {
        case Ok(resp) => Task.now("Ok")
        case _ => Task.now("fail")
      } must returnValue("Ok")
    }

    "get disposes of the response on success" in {
      assertDisposes(_.get(req.uri) { _ => Task.now(()) })
    }

    "get disposes of the response on failure" in {
      assertDisposes(_.get(req.uri) { _ => Task.fail(SadTrombone) })
    }

    "get disposes of the response on uncaught exception" in {
      assertDisposes(_.get(req.uri) { _ => sys.error("Don't do this at home, kids") })
    }

    "fetch disposes of the response on success" in {
      assertDisposes(_.fetch(req) { _ => Task.now(()) })
    }

    "fetch disposes of the response on failure" in {
      assertDisposes(_.fetch(req) { _ => Task.fail(SadTrombone) })
    }

    "fetch disposes of the response on uncaught exception" in {
      assertDisposes(_.fetch(req) { _ => sys.error("Don't do this at home, kids") })
    }

    "fetch on task disposes of the response on success" in {
      assertDisposes(_.fetch(Task.now(req)) { _ => Task.now(()) })
    }

    "fetch on task disposes of the response on failure" in {
      assertDisposes(_.fetch(Task.now(req)) { _ => Task.fail(SadTrombone) })
    }

    "fetch on task disposes of the response on uncaught exception" in {
      assertDisposes(_.fetch(Task.now(req)) { _ => sys.error("Don't do this at home, kids") })
    }

    "fetch on task that does not match results in failed task" in {
      client.fetch(Task.now(req))(PartialFunction.empty).attempt.unsafeRun must beLeft { e: Throwable => e must beAnInstanceOf[MatchError] }
    }

    "fetch Uris with expect" in {
      client.expect[String](req.uri) must returnValue("hello")
    }

    "fetch requests with expect" in {
      client.expect[String](req) must returnValue("hello")
    }

    "fetch request tasks with expect" in {
      client.expect[String](Task.now(req)) must returnValue("hello")
    }

    "return an unexpected status when expect returns unsuccessful status" in {
      client.expect[String](uri("http://www.foo.com/status/500")).attempt must returnValue(Left(UnexpectedStatus(Status.InternalServerError)))
    }

    "add Accept header on expect" in {
      client.expect[String](uri("http://www.foo.com/echoheaders")) must returnValue("Accept: text/*")
    }

    "add Accept header on expect for requests" in {
      client.expect[String](Request(GET, uri("http://www.foo.com/echoheaders"))) must returnValue("Accept: text/*")
    }

    "add Accept header on expect for requests" in {
      client.expect[String](GET(uri("http://www.foo.com/echoheaders"))) must returnValue("Accept: text/*")
    }

     "combine entity decoder media types correctly" in {
       // This is more of an EntityDecoder spec
       val edec = EntityDecoder.decodeBy(MediaType.`image/jpeg`)(_ => DecodeResult.success("foo!"))
       client.expect(GET(uri("http://www.foo.com/echoheaders")))(EntityDecoder.text orElse edec) must returnValue("Accept: text/*, image/jpeg")
     }

     "streaming returns a stream" in {
       client.streaming(req)(_.body.through(fs2.text.utf8Decode)).runLog.unsafeRun() must_== Vector("hello")
     }

     "streaming disposes of the response on success" in {
       assertDisposes(_.streaming(req)(_.body).run)
     }

     "streaming disposes of the response on failure" in {
       assertDisposes(_.streaming(req)(_ => Stream.fail(SadTrombone)).run)
     }

     "toService disposes of the response on success" in {
       assertDisposes(_.toService(_ => Task.now(())).run(req))
     }

     "toService disposes of the response on failure" in {
       assertDisposes(_.toService(_ => Task.fail(SadTrombone)).run(req))
     }

     "toHttpService disposes the response if the body is run" in {
       assertDisposes(_.toHttpService.flatMapF(_.orNotFound.body.run).run(req))
     }

     "toHttpService disposes of the response if the body is run, even if it fails" in {
       assertDisposes(_.toHttpService.flatMapF(_.orNotFound.body.flatMap(_ => Stream.fail(SadTrombone)).run).run(req))
     }

    "toHttpService allows the response to be read" in {
      client.toHttpService.orNotFound(req).as[String] must returnValue("hello")
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
