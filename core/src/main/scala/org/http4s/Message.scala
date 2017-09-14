package org.http4s

import java.io.File
import java.net.{InetAddress, InetSocketAddress}

import cats._
import cats.implicits._
import fs2._
import fs2.text._
import org.http4s.headers._
import org.http4s.server.ServerSoftware
import org.http4s.util.nonEmptyList._
import org.log4s.getLogger

/**
  * Represents a HTTP Message.
  */
trait Message[F[_], M[_[_]]] {

  def httpVersion(m: M[F]): HttpVersion
  def headers(m: M[F]): Headers
  def body(m: M[F]): EntityBody[F]
  def attributes(m: M[F]): AttributeMap
  def change(m: M[F])(
      b: EntityBody[F] = body(m),
      h: Headers = headers(m),
      a: AttributeMap = attributes(m)): M[F]

  /** Sets the entity body without affecting headers such as `Transfer-Encoding`
    * or `Content-Length`. Most use cases are better served by [[withBody]],
    * which uses an [[EntityEncoder]] to maintain the headers.
    */
  def withBodyStream(m: M[F])(body: EntityBody[F]): M[F] =
    change(m)(body, headers(m), attributes(m))

  def transformHeaders(m: M[F])(f: Headers => Headers)(implicit F: Functor[F]): M[F] =
    change(m)(h = f(headers(m)))

  final def bodyAsText(m: M[F])(
      implicit defaultCharset: Charset = DefaultCharset): Stream[F, String] =
    charset(m).getOrElse(defaultCharset) match {
      case Charset.`UTF-8` =>
        // suspect this one is more efficient, though this is superstition
        body(m).through(utf8Decode)
      case cs =>
        body(m).through(util.decode(cs))
    }

  /** True if and only if the body is composed solely of Emits and Halt. This
    * indicates that the body can be re-run without side-effects. */
  // TODO fs2 port need to replace unemit
  /*
  def isBodyPure: Boolean =
    body.unemit._2.isHalt
   */

  def withAttribute[A](m: M[F])(key: AttributeKey[A], value: A)(implicit F: Functor[F]): M[F] =
    change(m)(a = attributes(m).put(key, value))

  /** Replace the body of this message with a new body
    *
    * @param b body to attach to this method
    * @param w [[EntityEncoder]] with which to convert the body to an [[EntityBody]]
    * @tparam T type of the Body
    * @return a new message with the new body
    */
  def withBody[T](m: M[F])(b: T)(implicit F: Monad[F], w: EntityEncoder[F, T]): F[M[F]] =
    w.toEntity(b).flatMap { entity =>
      val hs = entity.length match {
        case Some(l) =>
          `Content-Length`
            .fromLong(l)
            .fold(
              _ =>
                F.pure {
                  Message.logger.warn(s"Attempt to provide a negative content length of $l")
                  w.headers.toList
              },
              cl => F.pure(cl :: w.headers.toList))
        case None => F.pure(w.headers.toList)
      }

      hs.map(newHeaders => change(m)(b = entity.body, h = headers(m) ++ newHeaders))
    }

  /** Set an empty entity body on this message, and remove all payload headers
    * that make no sense with an empty body.
    */
  def withEmptyBody(m: M[F])(implicit F: Functor[F]): M[F] =
    transformHeaders(withBodyStream(m)(EmptyBody))(_.removePayloadHeaders)

  def contentLength(m: M[F]): Option[Long] = headers(m).get(`Content-Length`).map(_.length)

  def contentType(m: M[F]): Option[`Content-Type`] = headers(m).get(`Content-Type`)

  /** Returns the charset parameter of the `Content-Type` header, if present. Does
    * not introspect the body for media types that define a charset
    * internally.
    */
  def charset(m: M[F]): Option[Charset] = contentType(m).flatMap(_.charset)

  def isChunked(m: M[F]): Boolean =
    headers(m).get(`Transfer-Encoding`).exists(_.values.contains(TransferCoding.chunked))

  /**
    * The trailer headers, as specified in Section 3.6.1 of RFC 2616.  The resulting
    * task might not complete unless the entire body has been consumed.
    */
  def trailerHeaders(m: M[F])(implicit F: Applicative[F]): F[Headers] =
    attributes(m).get(Message.Keys.TrailerHeaders[F]).getOrElse(F.pure(Headers.empty))

  /** Decode the [[Message]] to the specified type
    *
    * @param decoder [[EntityDecoder]] used to decode the [[Message]]
    * @tparam T type of the result
    * @return the effect which will generate the `DecodeResult[T]`
    */
  def attemptAs[T](
      m: M[F])(implicit F: FlatMap[F], decoder: EntityDecoder[F, T, M]): DecodeResult[F, T] =
    decoder.decode(m, strict = false)

  /** Remove headers that satisfy the predicate
    *
    * @param f predicate
    * @return a new message object which lacks the specified headers
    */
  final def filterHeaders(m: M[F])(f: Header => Boolean)(implicit F: Functor[F]): M[F] =
    transformHeaders(m)(_.filter(f))

  /** Generates a new message object with the specified key/value pair appended to the [[org.http4s.AttributeMap]]
    *
    * @param entry [[AttributeEntry]] entry to add
    * @tparam V type of the value to store
    * @return a new message object with the key/value pair appended
    */
  def withAttributeEntry[V](m: M[F])(entry: AttributeEntry[V])(implicit F: Functor[F]): M[F] =
    withAttribute(m)(entry.key, entry.value)

  /** Added the [[org.http4s.headers.Content-Type]] header to the response */
  final def withType(m: M[F])(t: MediaType)(implicit F: Functor[F]): M[F] =
    putHeaders(m)(`Content-Type`(t))

  final def withContentType(m: M[F])(contentType: Option[`Content-Type`])(
      implicit F: Functor[F]): M[F] =
    contentType match {
      case Some(t) => putHeaders(m)(t)
      case None => filterHeaders(m)(_.is(`Content-Type`))
    }

  final def removeHeader(m: M[F])(key: HeaderKey)(implicit F: Functor[F]): M[F] =
    filterHeaders(m)(_.isNot(key))

  /** Replaces the [[Header]]s of the incoming Request object
    *
    * @param headers [[Headers]] containing the desired headers
    * @return a new Request object
    */
  final def replaceAllHeaders(m: M[F])(headers: Headers)(implicit F: Functor[F]): M[F] =
    transformHeaders(m)(_ => headers)

  /** Replace the existing headers with those provided */
  final def replaceAllHeaderSeq(m: M[F])(headers: Header*)(implicit F: Functor[F]): M[F] =
    replaceAllHeaders(m)(Headers(headers.toList))

  /** Add the provided headers to the existing headers, replacing those of the same header name
    * The passed headers are assumed to contain no duplicate Singleton headers.
    */
  final def putHeaders(m: M[F])(headers: Header*)(implicit F: Functor[F]): M[F] =
    transformHeaders(m)(_.put(headers: _*))

  final def withTrailerHeaders(m: M[F])(trailerHeaders: F[Headers])(implicit F: Functor[F]): M[F] =
    withAttribute(m)(Message.Keys.TrailerHeaders[F], trailerHeaders)

  /** Decode the [[Message]] to the specified type
    *
    * If no valid [[Status]] has been described, allow Ok
    * @param decoder [[EntityDecoder]] used to decode the [[Message]]
    * @tparam T type of the result
    * @return the effect which will generate the T
    */
  final def as[T](m: M[F])(implicit F: FlatMap[F], decoder: EntityDecoder[F, T]): F[T] =
    attemptAs(m).fold(throw _, identity)

}

object Message {
  private[http4s] val logger = getLogger
  object Keys {
    private[this] val trailerHeaders: AttributeKey[Any] = AttributeKey[Any]
    def TrailerHeaders[F[_]]: AttributeKey[F[Headers]] =
      trailerHeaders.asInstanceOf[AttributeKey[F[Headers]]]
  }

  def apply[F[_], M[_[_]]](implicit M: Message[F, M]): Message[F, M] = M

  implicit class MessageOps[F[_], M[_[_]]](m: M[F])(implicit ev: Message[F, M]) {

    def httpVersion: HttpVersion = Message[F, M].httpVersion(m)

    def headers: Headers = Message[F, M].headers(m)

    def body: EntityBody[F] = Message[F, M].body(m)

    def attributes: AttributeMap = Message[F, M].attributes(m)

    def change(
        body: EntityBody[F] = body,
        headers: Headers = headers,
        attributes: AttributeMap = attributes): M[F] =
      Message[F, M].change(m)(body, headers, attributes)

  }

}
