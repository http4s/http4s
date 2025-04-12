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

package org.http4s
package dsl

import cats.Monad
import cats.effect.IO
import cats.syntax.all._
import org.http4s.dsl.io._
import org.http4s.headers.Accept
import org.http4s.headers.Location
import org.http4s.headers.`Content-Length`
import org.http4s.headers.`Content-Type`
import org.http4s.syntax.literals._

import scala.annotation.nowarn

class ResponseGeneratorSuite extends Http4sSuite {
  test("Add the EntityEncoder headers along with a content-length header") {
    val body = "foo"
    val resultheaders = Ok(body)(Monad[IO], EntityEncoder.stringEncoder[IO]).map(_.headers)
    EntityEncoder
      .stringEncoder[IO]
      .headers
      .headers
      .traverse { h =>
        resultheaders.map(_.headers.exists(_ == h)).assert
      } *>
      resultheaders
        .map(_.get[`Content-Length`])
        .assertEquals(
          `Content-Length`
            .fromLong(body.getBytes.length.toLong)
            .toOption
        )
  }

  test("Not duplicate headers when not provided") {
    val w =
      EntityEncoder.encodeBy[IO, String](
        EntityEncoder.stringEncoder[IO].headers.put(Accept(MediaRange.`audio/*`))
      )(
        EntityEncoder.stringEncoder[IO].toEntity(_)
      )

    Ok("foo")(Monad[IO], w)
      .map(_.headers.get[Accept])
      .assertEquals(Some(Accept(MediaRange.`audio/*`)))
  }

  test("Explicitly added headers have priority") {
    val w: EntityEncoder[IO, String] = EntityEncoder.encodeBy[IO, String](
      EntityEncoder.stringEncoder[IO].headers.put(`Content-Type`(MediaType.text.html))
    )(
      EntityEncoder.stringEncoder[IO].toEntity(_)
    )

    val resp: IO[Response[IO]] =
      Ok("foo", `Content-Type`(MediaType.application.json))(Monad[IO], w)
    resp
      .map(_.headers.get[`Content-Type`].map(_.mediaType))
      .assertEquals(Some(MediaType.application.json))
  }

  test("NoContent() does not generate Content-Length") {
    /* A server MUST NOT send a Content-Length header field in any response
     * with a status code of 1xx (Informational) or 204 (No Content).
     * -- https://datatracker.ietf.org/doc/html/rfc7230#section-3.3.2
     */
    val resp = NoContent()
    resp.map(_.contentLength).assertEquals(Option.empty[Long])
  }

  test("ResetContent() generates Content-Length: 0") {
    /* a server MUST do one of the following for a 205 response: a) indicate a
     * zero-length body for the response by including a Content-Length header
     * field with a value of 0; b) indicate a zero-length payload for the
     * response by including a Transfer-Encoding header field with a value of
     * chunked and a message body consisting of a single chunk of zero-length;
     * or, c) close the connection immediately after sending the blank line
     * terminating the header section.
     * -- https://datatracker.ietf.org/doc/html/rfc7231#section-6.3.6
     *
     * We choose option a.
     */
    val resp = ResetContent()
    resp.map(_.contentLength).assertEquals(Some(0L))
  }

  test("NotModified() does not generate Content-Length") {
    /* A server MAY send a Content-Length header field in a 304 (Not Modified)
     * response to a conditional GET request (Section 4.1 of [RFC7232]); a
     * server MUST NOT send Content-Length in such a response unless its
     * field-value equals the decimal number of octets that would have been sent
     * in the payload body of a 200 (OK) response to the same request.
     * -- https://datatracker.ietf.org/doc/html/rfc7230#section-3.3.2
     *
     * We don't know what the proper value is in this signature, so we send
     * nothing.
     */
    val resp = NotModified()
    resp.map(_.contentLength).assertEquals(Option.empty[Long])
  }

  test("EntityResponseGenerator() generates Content-Length: 0") {

    /** Aside from the cases defined above, in the absence of Transfer-Encoding,
      * an origin server SHOULD send a Content-Length header field when the
      * payload body size is known prior to sending the complete header section.
      * -- https://datatracker.ietf.org/doc/html/rfc7230#section-3.3.2
      *
      * Until someone sets a body, we have an empty body and we'll set the
      * Content-Length.
      */
    val resp = Ok()
    resp.map(_.contentLength).assertEquals(Some(0L))
  }

  test("MovedPermanently() generates expected headers without body") {
    val location = Location(uri"http://foo")
    val resp = MovedPermanently(location, (), Accept(MediaRange.`audio/*`))
    resp
      .map(_.headers.headers)
      .assertEquals(
        Headers(
          location,
          Accept(MediaRange.`audio/*`),
          `Content-Length`.zero,
        ).headers
      )
  }

  test("MovedPermanently() generates expected headers with body") {
    val location = Location(uri"http://foo")
    val body = "foo"
    val resp = MovedPermanently(location, body, Accept(MediaRange.`audio/*`))
    resp
      .map(_.headers.headers)
      .assertEquals(
        Headers(
          `Content-Type`(MediaType.text.plain, Charset.`UTF-8`),
          location,
          Accept(MediaRange.`audio/*`),
          `Content-Length`.unsafeFromLong(3),
        ).headers
      )
  }

  test("UnprocessableEntity() has unambiguous and deprecated implicit") {
    val _ = UnprocessableEntity(): @nowarn("cat=deprecation")
  }
}
