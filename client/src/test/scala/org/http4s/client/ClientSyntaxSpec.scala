package org.http4s
package client

import org.http4s.Http4sSpec
import org.http4s.headers.Accept

import scalaz.concurrent.Task
import scalaz.stream.Process

import org.http4s.Status.{Ok, NotFound, Created, BadRequest}
import org.http4s.Method._

import org.specs2.matcher.MustThrownMatchers

class ClientSyntaxSpec extends Http4sSpec with MustThrownMatchers {

  val route = HttpService {
    case r if r.method == GET && r.pathInfo == "/"            => Response(Ok).withBody("hello")
    case r if r.method == PUT && r.pathInfo == "/put"         => Response(Created).withBody(r.body)
    case r if r.method == GET && r.pathInfo == "/echoheaders" =>
      r.headers.get(Accept).fold(Task.now(Response(BadRequest))){ m =>
         Response(Ok).withBody(m.toString)
      }

    case r => sys.error("Path not found: " + r.pathInfo)
  }

  val client = MockClient(route)

  val req = Request(GET, uri("http://www.foo.bar/"))

  object SadTrombone extends Exception("sad trombone")

  def assertDisposes(f: Client => Task[Unit]) = {
    var disposed = false
    val disposingClient = MockClient(route, Task.delay(disposed = true))
    f(disposingClient).attemptRun
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

    "fetch disposes of the response on success" in {
      assertDisposes(_.fetch(req) { _ => Task.now(()) })
    }

    "fetch disposes of the response on failure" in {
      assertDisposes(_.fetch(req) { _ => Task.fail(SadTrombone) })
    }

    "fetch on task disposes of the response on success" in {
      assertDisposes(_.fetch(Task.now(req)) { _ => Task.now(()) })
    }

    "fetch on task disposes of the response on failure" in {
      assertDisposes(_.fetch(Task.now(req)) { _ => Task.fail(SadTrombone) })
    }

    "fetch Uris with getAs" in {
      client.getAs[String](req.uri) must returnValue("hello")
    }

    "fetch requests with fetchAs" in {
      client.fetchAs[String](req) must returnValue("hello")
    }

    "fetch request tasks with fetchAs" in {
      client.fetchAs[String](Task.now(req)) must returnValue("hello")
    }

    "add Accept header on getAs" in {
      client.getAs[String](uri("http://www.foo.com/echoheaders")) must returnValue("Accept: text/*")
    }

    "add Accept header on fetchAs for requests" in {
      client.fetchAs[String](Request(GET, uri("http://www.foo.com/echoheaders"))) must returnValue("Accept: text/*")
    }

    "add Accept header on fetchAs for requests" in {
      client.fetchAs[String](GET(uri("http://www.foo.com/echoheaders"))) must returnValue("Accept: text/*")
    }

    "combine entity decoder media types correctly" in {
      // This is more of an EntityDecoder spec
      val edec = EntityDecoder.decodeBy(MediaType.`image/jpeg`)(_ => DecodeResult.success("foo!"))
      client.fetchAs(GET(uri("http://www.foo.com/echoheaders")))(EntityDecoder.text orElse edec) must returnValue("Accept: text/*, image/jpeg")
    }

    "streaming returns a stream" in {
      client.streaming(req)(_.body.pipe(scalaz.stream.text.utf8Decode)).runLog.run must_== Vector("hello")
    }

    "streaming disposes of the response on success" in {
      assertDisposes(_.streaming(req)(_.body).run)
    }

    "streaming disposes of the response on failure" in {
      assertDisposes(_.streaming(req)(_ => Process.fail(SadTrombone).toSource).run)
    }

    "toService disposes of the response on success" in {
      assertDisposes(_.toService(_ => Task.now(())).run(req))
    }

    "toService disposes of the response on failure" in {
      assertDisposes(_.toService(_ => Task.fail(SadTrombone)).run(req))
    }

    "toHttpService disposes the response if the body is run" in {
      assertDisposes(_.toHttpService.flatMapK(_.body.run).run(req))
    }

    "toHttpService disposes of the response if the body is run, even if it fails" in {
      assertDisposes(_.toHttpService.flatMapK(_.body.flatMap(_ => Process.fail(SadTrombone).toSource).run).run(req))
    }
  }

  "RequestResponseGenerator" should {
    "Generate requests based on Method" in {
      client.fetchAs[String](GET(uri("http://www.foo.com/"))) must returnValue("hello")

      // The PUT: /put path just echoes the body
      client.fetchAs[String](PUT(uri("http://www.foo.com/put"), "hello?")) must returnValue("hello?")
    }
  }
}
