package org.http4s

import java.time.{ZoneOffset, Instant}

import org.http4s.headers.{`Set-Cookie`, `Content-Type`}
import org.http4s.util.NonEmptyList._

import scalaz.\/
import scalaz.concurrent.Task

trait MessageOps extends Any {
  type Self

  /** Remove headers that satisfy the predicate
    *
    * @param f predicate
    * @return a new message object which lacks the specified headers
    */
  final def filterHeaders(f: Header => Boolean): Self =
    transformHeaders(_.filter(f))

  /** Generates a new message object with the specified key/value pair appended to the [[org.http4s.AttributeMap]]
    *
    * @param key [[AttributeKey]] with which to associate the value
    * @param value value associated with the key
    * @tparam A type of the value to store
    * @return a new message object with the key/value pair appended
    */
  def withAttribute[A](key: AttributeKey[A], value: A): Self

  /** Generates a new message object with the specified key/value pair appended to the [[org.http4s.AttributeMap]]
    *
    * @param entry [[AttributeEntry]] entry to add
    * @tparam V type of the value to store
    * @return a new message object with the key/value pair appended
    */
  def withAttribute[V](entry: AttributeEntry[V]): Self = withAttribute(entry.key, entry.value)

  def transformHeaders(f: Headers => Headers): Self

  /** Added the [[org.http4s.headers.Content-Type]] header to the response */
  final def withType(t: MediaType): Self =
    putHeaders(`Content-Type`(t))

  final def withContentType(contentType: Option[`Content-Type`]): Self =
    contentType match {
      case Some(t) => putHeaders(t)
      case None => filterHeaders(_.is(`Content-Type`))
    }

  final def removeHeader(key: HeaderKey): Self = filterHeaders(_ isNot key)

  /** Replaces the [[Header]]s of the incoming Request object
    *
    * @param headers [[Headers]] containing the desired headers
    * @return a new Request object
    */
  final def replaceAllHeaders(headers: Headers): Self =
    transformHeaders(_ => headers)

  /** Replace the existing headers with those provided */
  final def replaceAllHeaders(headers: Header*): Self =
    replaceAllHeaders(Headers(headers.toList))

  /** Add the provided headers to the existing headers, replacing those of the same header name
    * The passed headers are assumed to contain no duplicate Singleton headers.
    */
  final def putHeaders(headers: Header*): Self =
    transformHeaders(_.put(headers: _*))

  final def withTrailerHeaders(trailerHeaders: Task[Headers]): Self =
    withAttribute(Message.Keys.TrailerHeaders, trailerHeaders)

  /** Decode the [[Message]] to the specified type
    *
    * @param decoder [[EntityDecoder]] used to decode the [[Message]]
    * @tparam T type of the result
    * @return the `Task` which will generate the `DecodeResult[T]`
    */
  def attemptAs[T](implicit decoder: EntityDecoder[T]): DecodeResult[T]

  /** Decode the [[Message]] to the specified type
    *
    * If no valid [[Status]] has been described, allow Ok
    * @param decoder [[EntityDecoder]] used to decode the [[Message]]
    * @tparam T type of the result
    * @return the `Task` which will generate the T
    */
  final def as[T](implicit decoder: EntityDecoder[T]): Task[T] =
    attemptAs(decoder).fold(throw _, identity)
}

trait RequestOps extends Any with MessageOps {
  def withPathInfo(pi: String): Self

  /** Helper method for decoding [[Request]]s
    *
    * Attempt to decode the [[Request]] and, if successful, execute the continuation to get a [[Response]].
    * If decoding fails, an `UnprocessableEntity` [[Response]] is generated.
    */
  final def decode[A](f: A => Task[Response])(implicit decoder: EntityDecoder[A]): Task[Response] =
    decodeWith(decoder, strict = false)(f)

  /** Helper method for decoding [[Request]]s
    *
    * Attempt to decode the [[Request]] and, if successful, execute the continuation to get a [[Response]].
    * If decoding fails, an `UnprocessableEntity` [[Response]] is generated. If the decoder does not support the
    * [[MediaType]] of the [[Request]], a `UnsupportedMediaType` [[Response]] is generated instead.
    */
  final def decodeStrict[A](f: A => Task[Response])(implicit decoder: EntityDecoder[A]): Task[Response] =
    decodeWith(decoder, true)(f)

  /** Like [[decode]], but with an explicit decoder.
    * @param strict If strict, will return a [[Status.UnsupportedMediaType]] http Response if this message's
    *               [[MediaType]] is not supported by the provided decoder
    */
  def decodeWith[A](decoder: EntityDecoder[A], strict: Boolean)(f: A => Task[Response]): Task[Response]

  /** Add a Cookie header for the provided [[Cookie]] */
  final def addCookie(cookie: Cookie): Self =
    putHeaders(org.http4s.headers.Cookie(nels(cookie)))

  /** Add a Cookie header with the provided values */
  final def addCookie(name: String,
                      content: String,
                      expires: Option[HttpDate] = None): Self =
    addCookie(Cookie(name, content, expires))
}

trait ResponseOps extends Any with MessageOps {
  /** Change the status of this response object
    *
    * @param status value to replace on the response object
    * @return a new response object with the new status code
    */
  def withStatus(status: Status): Self

  /** Add a Set-Cookie header for the provided [[Cookie]] */
  final def addCookie(cookie: Cookie): Self =
    putHeaders(`Set-Cookie`(cookie))

  /** Add a Set-Cookie header with the provided values */
  final def addCookie(name: String,
                      content: String,
                      expires: Option[HttpDate] = None): Self =
    addCookie(Cookie(name, content, expires))

  /** Add a [[org.http4s.headers.Set-Cookie]] which will remove the specified cookie from the client */
  final def removeCookie(cookie: Cookie): Self = putHeaders(`Set-Cookie`(cookie.copy(content = "",
    expires = Some(HttpDate.Epoch), maxAge = Some(0))))

  /** Add a [[org.http4s.headers.Set-Cookie]] which will remove the specified cookie from the client */
  final def removeCookie(name: String): Self = putHeaders(`Set-Cookie`(
    Cookie(name, "", expires = Some(HttpDate.Epoch), maxAge = Some(0))
  ))
}
