package org.http4s

import org.http4s.headers.{`Set-Cookie`, `Content-Type`}

import scalaz.\/
import scalaz.concurrent.Task

trait MessageOps extends Any {
  type Self

  /** Remove headers that satisfy the predicate
    *
    * @param f predicate
    * @return a new message object which lacks the specified headers
    */
  def filterHeaders(f: Header => Boolean): Self

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

  /** Added the [[`Content-Type`]] header to the response */
  def withType(t: MediaType): Self = putHeaders(`Content-Type`(t))

  def withContentType(contentType: Option[`Content-Type`]): Self = contentType match {
    case Some(t) => putHeaders(t)
    case None => filterHeaders(_.is(`Content-Type`))
  }

  def removeHeader(key: HeaderKey): Self = filterHeaders(_ isNot key)
/** Replaces the [[Header]]s of the incoming Request object
    *
    * @param headers [[Headers]] containing the desired headers
    * @return a new Request object
    */
  @deprecated("Use replaceAllHeaders. Will be removed in 0.11.x", "0.10.0")
  final def withHeaders(headers: Headers): Self = replaceAllHeaders(headers)

  /** Replace the existing headers with those provided */
  @deprecated("Use replaceAllHeaders. Will be removed in 0.11.x", "0.10.0")
  final def withHeaders(headers: Header*): Self = replaceAllHeaders(Headers(headers.toList))
  
  /** Replaces the [[Header]]s of the incoming Request object
    *
    * @param headers [[Headers]] containing the desired headers
    * @return a new Request object
    */
  def replaceAllHeaders(headers: Headers): Self

  /** Replace the existing headers with those provided */
  def replaceAllHeaders(headers: Header*): Self = replaceAllHeaders(Headers(headers.toList))

  /** Add the provided headers to the existing headers, replacing those of the same header name
    * The passed headers are assumed to contain no duplicate Singleton headers.
    */
  def putHeaders(headers: Header*): Self

  def withTrailerHeaders(trailerHeaders: Task[Headers]): Self =
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
  def as[T](implicit decoder: EntityDecoder[T]): Task[T] =
    attemptAs(decoder).fold(e => throw DecodeFailureException(e), identity)
}

trait ResponseOps extends Any with MessageOps {

  /** Change the status of this response object
    *
    * @param status value to replace on the response object
    * @tparam S type that can be converted to a [[Status]]
    * @return a new response object with the new status code
    */
  def withStatus[S <% Status](status: S): Self

  /** Add a Set-Cookie header for the provided [[Cookie]] */
  def addCookie(cookie: Cookie): Self = putHeaders(`Set-Cookie`(cookie))

  /** Add a Set-Cookie header with the provided values */
  def addCookie(name: String,
                content: String,
                expires: Option[DateTime] = None): Self = addCookie(Cookie(name, content, expires))

  /** Add a [[`Set-Cookie`]] which will remove the specified cookie from the client */
  def removeCookie(cookie: Cookie): Self = putHeaders(`Set-Cookie`(cookie.copy(content = "",
    expires = Some(DateTime.UnixEpoch), maxAge = Some(0))))

  /** Add a Set-Cookie which will remove the specified cookie from the client */
  def removeCookie(name: String): Self = putHeaders(`Set-Cookie`(
    Cookie(name, "", expires = Some(DateTime.UnixEpoch), maxAge = Some(0))
  ))
}

