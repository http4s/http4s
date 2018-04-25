package org.http4s
package dsl

import cats._
import cats.effect.IO
import org.http4s.dsl.io._
import org.http4s.MediaType
import org.http4s.headers.{Accept, Location, `Content-Length`, `Content-Type`}

class ResponseGeneratorSpec extends Http4sSpec {

  "Add the EntityEncoder headers along with a content-length header" in {
    val body = "foo"
    val resultheaders = Ok(body)(Monad[IO], EntityEncoder.stringEncoder[IO]).unsafeRunSync.headers
    EntityEncoder.stringEncoder[IO].headers.foldLeft(ok) { (old, h) =>
      old.and(resultheaders.exists(_ == h) must_=== true)
    }

    resultheaders.get(`Content-Length`) must_=== `Content-Length`
      .fromLong(body.getBytes.length.toLong)
      .right
      .toOption
  }

  "Not duplicate headers when not provided" in {
    val w =
      EntityEncoder.encodeBy[IO, String](
        EntityEncoder.stringEncoder[IO].headers.put(Accept(MediaRange.`audio/*`)))(
        EntityEncoder.stringEncoder[IO].toEntity(_)
      )

    Ok("foo")(Monad[IO], w).map(_.headers.get(Accept)) must returnValue(
      beSome(Accept(MediaRange.`audio/*`)))
  }

  "Explicitly added headers have priority" in {
    val w: EntityEncoder[IO, String] = EntityEncoder.encodeBy[IO, String](
      EntityEncoder.stringEncoder[IO].headers.put(`Content-Type`(MediaType.text.html)))(
      EntityEncoder.stringEncoder[IO].toEntity(_)
    )

    val resp: IO[Response[IO]] =
      Ok("foo", `Content-Type`(MediaType.application json))(Monad[IO], w)
    resp must returnValue(haveMediaType(MediaType.application json))
  }

  "NoContent() does not generate Content-Length" in {
    /* A server MUST NOT send a Content-Length header field in any response
     * with a status code of 1xx (Informational) or 204 (No Content).
     * -- https://tools.ietf.org/html/rfc7230#section-3.3.2
     */
    val resp = NoContent()
    resp.map(_.contentLength) must returnValue(None)
  }

  "ResetContent() generates Content-Length: 0" in {
    /* a server MUST do one of the following for a 205 response: a) indicate a
     * zero-length body for the response by including a Content-Length header
     * field with a value of 0; b) indicate a zero-length payload for the
     * response by including a Transfer-Encoding header field with a value of
     * chunked and a message body consisting of a single chunk of zero-length;
     * or, c) close the connection immediately after sending the blank line
     * terminating the header section.
     * -- https://tools.ietf.org/html/rfc7231#section-6.3.6
     *
     * We choose option a.
     */
    val resp = ResetContent()
    resp.map(_.contentLength) must returnValue(Some(0))
  }

  "NotModified() does not generate Content-Length" in {
    /* A server MAY send a Content-Length header field in a 304 (Not Modified)
     * response to a conditional GET request (Section 4.1 of [RFC7232]); a
     * server MUST NOT send Content-Length in such a response unless its
     * field-value equals the decimal number of octets that would have been sent
     * in the payload body of a 200 (OK) response to the same request.
     * -- https://tools.ietf.org/html/rfc7230#section-3.3.2
     *
     * We don't know what the proper value is in this signature, so we send
     * nothing.
     */
    val resp = NotModified()
    resp.map(_.contentLength) must returnValue(None)
  }

  "EntityResponseGenerator() generates Content-Length: 0" in {

    /**
      * Aside from the cases defined above, in the absence of Transfer-Encoding,
      * an origin server SHOULD send a Content-Length header field when the
      * payload body size is known prior to sending the complete header section.
      * -- https://tools.ietf.org/html/rfc7230#section-3.3.2
      *
      * Until someone sets a body, we have an empty body and we'll set the
      * Content-Length.
      */
    val resp = Ok()
    resp.map(_.contentLength) must returnValue(Some(0))
  }

  "MovedPermanently() generates expected headers without body" in {
    val location = Location(Uri.unsafeFromString("http://foo"))
    val resp = MovedPermanently(location, Accept(MediaRange.`audio/*`))
    resp must returnValue(
      haveHeaders(
        Headers(
          `Content-Length`.zero,
          location,
          Accept(MediaRange.`audio/*`)
        )))
  }

  "MovedPermanently() generates expected headers with body" in {
    val location = Location(Uri.unsafeFromString("http://foo"))
    val body = "foo"
    val resp = MovedPermanently(location, body, Accept(MediaRange.`audio/*`))
    resp must returnValue(
      haveHeaders(
        Headers(
          `Content-Type`(MediaType.text.plain, Charset.`UTF-8`),
          location,
          Accept(MediaRange.`audio/*`),
          `Content-Length`.unsafeFromLong(3)
        )))
  }
}
