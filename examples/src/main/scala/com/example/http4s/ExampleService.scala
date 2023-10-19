/*
 * Copyright 2013 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.http4s

import cats.effect._
import cats.syntax.all._
import fs2.Stream
import io.circe.Json
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.headers._
import org.http4s.multipart.Multipart
import org.http4s.server._
import org.http4s.server.middleware.authentication.BasicAuth
import org.http4s.server.middleware.authentication.BasicAuth.BasicAuthenticator
import org.http4s.syntax.all._

import scala.concurrent.duration._

class ExampleService[F[_]](implicit F: Async[F]) extends Http4sDsl[F] {
  // A Router can mount multiple services to prefixes.  The request is passed to the
  // service with the longest matching prefix.
  def routes: HttpRoutes[F] =
    Router[F](
      "" -> rootRoutes,
      "/auth" -> authRoutes,
    )

  def rootRoutes: HttpRoutes[F] =
    HttpRoutes.of[F] {
      case GET -> Root =>
        // disabled until twirl supports dotty
        // Supports Play Framework template -- see src/main/twirl.
        // Ok(html.index())
        Ok("Hello World")

      case _ -> Root =>
        // The default route result is NotFound. Sometimes MethodNotAllowed is more appropriate.
        MethodNotAllowed(Allow(GET))

      case GET -> Root / "ping" =>
        // EntityEncoder allows for easy conversion of types to a response body
        Ok("pong")

      case GET -> Root / "streaming" =>
        // It's also easy to stream responses to clients
        Ok(dataStream(100))

      case req @ GET -> Root / "ip" =>
        // It's possible to define an EntityEncoder anywhere so you're not limited to built in types
        val json = Json.obj("origin" -> Json.fromString(req.remoteAddr.fold("unknown")(_.toString)))
        Ok(json)

      case GET -> Root / "redirect" =>
        // Not every response must be Ok using a EntityEncoder: some have meaning only for specific types
        TemporaryRedirect(Location(uri"/http4s/"))

      case GET -> Root / "content-change" =>
        // EntityEncoder typically deals with appropriate headers, but they can be overridden
        Ok("<h2>This will have an html content type!</h2>", `Content-Type`(MediaType.text.html))

      case req @ GET -> "static" /: path =>
        // captures everything after "/static" into `path`
        // Try http://localhost:8080/http4s/static/nasa_blackhole_image.jpg
        // See also org.http4s.server.staticcontent to create a mountable service for static content
        StaticFile.fromResource(path.toString, Some(req)).getOrElseF(NotFound())

      // /////////////////////////////////////////////////////////////
      // ////////////// Dealing with the message body ////////////////
      case req @ POST -> Root / "echo" =>
        // The body can be used in the response
        Ok(req.body).map(_.putHeaders(`Content-Type`(MediaType.text.plain)))

      case GET -> Root / "echo" =>
        // disabled until twirl supports dotty
        // Ok(html.submissionForm("echo data"))
        Ok("Hello World")

      case req @ POST -> Root / "echo2" =>
        // Even more useful, the body can be transformed in the response
        Ok(req.body.drop(6), `Content-Type`(MediaType.text.plain))

      case GET -> Root / "echo2" =>
        // disabled until twirl supports dotty
        // Ok(html.submissionForm("echo data"))
        Ok("Hello World")

      case req @ POST -> Root / "sum" =>
        // EntityDecoders allow turning the body into something useful
        req
          .decode { (data: UrlForm) =>
            data.values.get("sum").flatMap(_.uncons) match {
              case Some((s, _)) =>
                val sum = s.split(' ').filter(_.nonEmpty).map(_.trim.toInt).sum
                Ok(sum.toString)

              case None => BadRequest(s"Invalid data: " + data)
            }
          }
          .recoverWith { // We can handle errors using effect methods
            case e: NumberFormatException => BadRequest("Not an int: " + e.getMessage)
          }

      case GET -> Root / "sum" =>
        // disabled until twirl supports dotty
        // Ok(html.submissionForm("sum"))
        Ok("Hello World")

      // /////////////////////////////////////////////////////////////
      // //////////////////// Blaze examples /////////////////////////

      // You can use the same service for GET and HEAD. For HEAD request,
      // only the Content-Length is sent (if static content)
      case GET -> Root / "helloworld" =>
        helloWorldService
      case HEAD -> Root / "helloworld" =>
        helloWorldService

      // HEAD responses with Content-Length, but empty content
      case HEAD -> Root / "head" =>
        Ok("", `Content-Length`.unsafeFromLong(1024))

      // Response with invalid Content-Length header generates
      // an error (underflow causes the connection to be closed)
      case GET -> Root / "underflow" =>
        Ok("foo", `Content-Length`.unsafeFromLong(4))

      // Response with invalid Content-Length header generates
      // an error (overflow causes the extra bytes to be ignored)
      case GET -> Root / "overflow" =>
        Ok("foo", `Content-Length`.unsafeFromLong(2))

      // /////////////////////////////////////////////////////////////
      // ////////////// Form encoding example ////////////////////////
      case GET -> Root / "form-encoded" =>
        // disabled until twirl supports dotty
        // Ok(html.formEncoded())
        Ok("Hello World")

      case req @ POST -> Root / "form-encoded" =>
        // EntityDecoders return an F[A] which is easy to sequence
        req.decode { (m: UrlForm) =>
          val s = m.values.mkString("\n")
          Ok(s"Form Encoded Data\n$s")
        }

      // /////////////////////////////////////////////////////////////
      // ////////////////////// Multi Part //////////////////////////
      case GET -> Root / "form" =>
        // disabled until twirl supports dotty
        // Ok(html.form())
        Ok("Hello World")

      case req @ POST -> Root / "multipart" =>
        req.decode { (m: Multipart[F]) =>
          Ok(s"""Multipart Data\nParts:${m.parts.length}\n${m.parts.map(_.name).mkString("\n")}""")
        }
    }

  def helloWorldService: F[Response[F]] = Ok("Hello World!")

  // This is a mock data source, but could be a Process representing results from a database
  def dataStream(n: Int)(implicit clock: Clock[F]): Stream[F, String] = {
    val interval = 100.millis
    val stream = Stream
      .awakeEvery[F](interval)
      .evalMap(_ => clock.realTime)
      .map(time => s"Current system time: $time ms\n")
      .take(n.toLong)

    Stream.emit(s"Starting $interval stream intervals, taking $n results\n\n") ++ stream
  }

  // Services can be protected using HTTP authentication.
  val realm = "testrealm"

  val authStore: BasicAuthenticator[F, String] = (creds: BasicCredentials) =>
    if (creds.username == "username" && creds.password == "password") F.pure(Some(creds.username))
    else F.pure(None)

  // An AuthedRoutes[A, F] is a Service[F, (A, Request[F]), Response[F]] for some
  // user type A.  `BasicAuth` is an auth middleware, which binds an
  // AuthedRoutes to an authentication store.
  val basicAuth: AuthMiddleware[F, String] = BasicAuth(realm, authStore)

  def authRoutes: HttpRoutes[F] =
    basicAuth(AuthedRoutes.of[String, F] {
      // AuthedRoutes look like HttpRoutes, but the user is extracted with `as`.
      case GET -> Root / "protected" as user =>
        Ok(s"This page is protected using HTTP authentication; logged in as $user")
    })
}

object ExampleService {
  def apply[F[_]: Async]: ExampleService[F] =
    new ExampleService[F]
}
