package com.example.http4s

// TODO fs2 port import io.circe.Json

import scala.concurrent._
import scala.concurrent.duration._

import fs2._
import org.http4s._
import org.http4s.MediaType._
import org.http4s.dsl._
import org.http4s.headers._
// TODO fs2 port import org.http4s.circe._
// TODO fs2 port import org.http4s.multipart._
// TODO fs2 port import org.http4s.scalaxml._
import org.http4s.server._
import org.http4s.server.middleware.PushSupport._
import org.http4s.server.middleware.authentication._
// TODO fs2 port import org.http4s.twirl._

object ExampleService {

  // A Router can mount multiple services to prefixes.  The request is passed to the
  // service with the longest matching prefix.
  def service(implicit executionContext: ExecutionContext = ExecutionContext.global): HttpService = Router(
    "" -> rootService,
    "/auth" -> authService
    // TODO fs2 port "/science" -> ScienceExperiments.service
  )

  def rootService(implicit executionContext: ExecutionContext) = HttpService {
    /* TODO fs2 port
    case req @ GET -> Root =>
      // Supports Play Framework template -- see src/main/twirl.
      Ok(html.index())
     */

    case _ -> Root =>
      // The default route result is NotFound. Sometimes MethodNotAllowed is more appropriate.
      MethodNotAllowed()

    case GET -> Root / "ping" =>
      // EntityEncoder allows for easy conversion of types to a response body
      Ok("pong")

    case GET -> Root / "future" =>
      // EntityEncoder allows rendering asynchronous results as well
      Ok(Future("Hello from the future!"))

    /* TODO fs2 port
    case GET -> Root / "streaming" =>
      // Its also easy to stream responses to clients
      Ok(dataStream(100))
     */

    /* TODO fs2 port
    case req @ GET -> Root / "ip" =>
      // Its possible to define an EntityEncoder anywhere so you're not limited to built in types
      val json = Json.obj("origin" -> Json.fromString(req.remoteAddr.getOrElse("unknown")))
      Ok(json)
     */

    case req @ GET -> Root / "redirect" =>
      // Not every response must be Ok using a EntityEncoder: some have meaning only for specific types
      TemporaryRedirect(uri("/http4s/"))

    case GET -> Root / "content-change" =>
      // EntityEncoder typically deals with appropriate headers, but they can be overridden
      Ok("<h2>This will have an html content type!</h2>")
          .withContentType(Some(`Content-Type`(`text/html`)))

    /* TODO fs2 port
    case req @ GET -> "static" /: path =>
      // captures everything after "/static" into `path`
      // Try http://localhost:8080/http4s/static/nasa_blackhole_image.jpg
      // See also org.http4s.server.staticcontent to create a mountable service for static content
      StaticFile.fromResource(path.toString, Some(req)).fold(NotFound())(Task.now)
     */

    ///////////////////////////////////////////////////////////////
    //////////////// Dealing with the message body ////////////////
    case req @ POST -> Root / "echo" =>
      // The body can be used in the response
      Ok(req.body).putHeaders(`Content-Type`(`text/plain`))

    /* TODO fs2 port
    case req @ GET -> Root / "echo" =>
      Ok(html.submissionForm("echo data"))

    case req @ POST -> Root / "echo2" =>
      // Even more useful, the body can be transformed in the response
      Ok(req.body.map(_.drop(6)))
        .putHeaders(`Content-Type`(`text/plain`))

    case req @ GET -> Root / "echo2" =>
      Ok(html.submissionForm("echo data"))
     */

    case req @ POST -> Root / "sum"  =>
      // EntityDecoders allow turning the body into something useful
      req.decode[UrlForm] { data =>
        data.values.get("sum") match {
          case Some(Seq(s, _*)) =>
            val sum = s.split(' ').filter(_.length > 0).map(_.trim.toInt).sum
            Ok(sum.toString)

          case None => BadRequest(s"Invalid data: " + data)
        }
      } handleWith {    // We can handle errors using Task methods
        case e: NumberFormatException => BadRequest("Not an int: " + e.getMessage)
      }

    /* TODO fs2 port
    case req @ GET -> Root / "sum" =>
      Ok(html.submissionForm("sum"))
     */

    ///////////////////////////////////////////////////////////////
    ////////////////////// Blaze examples /////////////////////////

    // You can use the same service for GET and HEAD. For HEAD request,
    // only the Content-Length is sent (if static content)
    case req @ GET -> Root / "helloworld" =>
      helloWorldService
    case req @ HEAD -> Root / "helloworld" =>
      helloWorldService

    // HEAD responses with Content-Lenght, but empty content
    case req @ HEAD -> Root / "head" =>
      Ok("").putHeaders(`Content-Length`(1024))

    // Response with invalid Content-Length header generates
    // an error (underflow causes the connection to be closed)
    case req @ GET -> Root / "underflow" =>
      Ok("foo").putHeaders(`Content-Length`(4))

    // Response with invalid Content-Length header generates
    // an error (overflow causes the extra bytes to be ignored)
    case req @ GET -> Root / "overflow" =>
      Ok("foo").putHeaders(`Content-Length`(2))

    ///////////////////////////////////////////////////////////////
    //////////////// Form encoding example ////////////////////////
     /* TODO fs2 port
    case req @ GET -> Root / "form-encoded" =>
      Ok(html.formEncoded())
       */

    case req @ POST -> Root / "form-encoded" =>
      // EntityDecoders return a Task[A] which is easy to sequence
      req.decode[UrlForm] { m =>
        val s = m.values.mkString("\n")
        Ok(s"Form Encoded Data\n$s")
      }

    ///////////////////////////////////////////////////////////////
    //////////////////////// Server Push //////////////////////////
      /* TODO fs2 port
    case req @ GET -> Root / "push" =>
      // http4s intends to be a forward looking library made with http2.0 in mind
      val data = <html><body><img src="image.jpg"/></body></html>
      Ok(data)
        .withContentType(Some(`Content-Type`(`text/html`)))
        .push("/image.jpg")(req)

    case req @ GET -> Root / "image.jpg" =>
      StaticFile.fromResource("/nasa_blackhole_image.jpg", Some(req))
        .map(Task.now)
        .getOrElse(NotFound())
       */

    ///////////////////////////////////////////////////////////////
    //////////////////////// Multi Part //////////////////////////
      /* TODO fs2 port
    case req @ GET -> Root / "form" =>
            println("FORM")
      Ok(html.form())

    case req @ POST -> Root / "multipart" =>
      println("MULTIPART")
      req.decode[Multipart] { m =>
        Ok(s"""Multipart Data\nParts:${m.parts.length}\n${m.parts.map { case f: Part => f.name }.mkString("\n")}""")
      }
       */
  }

  def helloWorldService = Ok("Hello World!")

  // This is a mock data source, but could be a Process representing results from a database
  // TODO fs2 port
  /*
  def dataStream(n: Int): Process[Task, String] = {
    implicit def defaultScheduler = DefaultTimeoutScheduler
    val interval = 100.millis
    val stream = time.awakeEvery(interval)
      .map(_ => s"Current system time: ${System.currentTimeMillis()} ms\n")
      .take(n)

    Process.emit(s"Starting $interval stream intervals, taking $n results\n\n") ++ stream
  }
   */

  // Services can be protected using HTTP authentication.
  val realm = "testrealm"

  def auth_store(r: String, u: String) = if (r == realm && u == "username") Task.now(Some("password"))
    else Task.now(None)

  val digest = digestAuth(realm, auth_store)

  // Digest is a middleware.  A middleware is a function from one service to another.
  // In this case, the wrapped service is protected with digest authentication.
  def authService = digest(AuthedService.apply[(String, String)]({
    case req @ GET -> Root / "protected" as ((user, realm)) => {
      Ok("This page is protected using HTTP authentication; logged in user/realm: " + user + "/" + realm)
    }
  }))
}
