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
abstract class Message[F[_], M[_]] {

  def httpVersion: HttpVersion

  def headers: Headers

  def body: EntityBody[F]

  def attributes: AttributeMap

  def change(
      body: EntityBody[F] = body,
      headers: Headers = headers,
      attributes: AttributeMap = attributes): M[F]

  /** Sets the entity body without affecting headers such as `Transfer-Encoding`
    * or `Content-Length`. Most use cases are better served by [[withBody]],
    * which uses an [[EntityEncoder]] to maintain the headers.
    */
  def withBodyStream(body: EntityBody[F]): M[F] =
    change(body, headers, attributes)

  def transformHeaders(f: Headers => Headers)(implicit F: Functor[F]): M[F] =
    change(headers = f(headers))

  final def bodyAsText(implicit defaultCharset: Charset = DefaultCharset): Stream[F, String] =
    charset.getOrElse(defaultCharset) match {
      case Charset.`UTF-8` =>
        // suspect this one is more efficient, though this is superstition
        body.through(utf8Decode)
      case cs =>
        body.through(util.decode(cs))
    }

  /** True if and only if the body is composed solely of Emits and Halt. This
    * indicates that the body can be re-run without side-effects. */
  // TODO fs2 port need to replace unemit
  /*
  def isBodyPure: Boolean =
    body.unemit._2.isHalt
   */

  def withAttribute[A](key: AttributeKey[A], value: A)(implicit F: Functor[F]): M[F] =
    change(attributes = attributes.put(key, value))

  /** Replace the body of this message with a new body
    *
    * @param b body to attach to this method
    * @param w [[EntityEncoder]] with which to convert the body to an [[EntityBody]]
    * @tparam T type of the Body
    * @return a new message with the new body
    */
  def withBody[T](b: T)(implicit F: Monad[F], w: EntityEncoder[F, T]): F[M[F]] =
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
        case None => F.pure(w.headers)
      }
      hs.map(newHeaders => change(body = entity.body, headers = headers ++ newHeaders))
    }

  /** Set an empty entity body on this message, and remove all payload headers
    * that make no sense with an empty body.
    */
  def withEmptyBody(implicit F: Functor[F], M: Message[F, M[F]]): M[F] =
    withBodyStream(EmptyBody).map(M.transformHeaders(_.removePayloadHeaders))

  def contentLength: Option[Long] = headers.get(`Content-Length`).map(_.length)

  def contentType: Option[`Content-Type`] = headers.get(`Content-Type`)

  /** Returns the charset parameter of the `Content-Type` header, if present. Does
    * not introspect the body for media types that define a charset
    * internally.
    */
  def charset: Option[Charset] = contentType.flatMap(_.charset)

  def isChunked: Boolean =
    headers.get(`Transfer-Encoding`).exists(_.values.contains(TransferCoding.chunked))

  /**
    * The trailer headers, as specified in Section 3.6.1 of RFC 2616.  The resulting
    * task might not complete unless the entire body has been consumed.
    */
  def trailerHeaders(implicit F: Applicative[F]): F[Headers] =
    attributes.get(Message.Keys.TrailerHeaders[F]).getOrElse(F.pure(Headers.empty))

  /** Decode the [[Message]] to the specified type
    *
    * @param decoder [[EntityDecoder]] used to decode the [[Message]]
    * @tparam T type of the result
    * @return the effect which will generate the `DecodeResult[T]`
    */
  override def attemptAs[T](
      implicit F: FlatMap[F],
      decoder: EntityDecoder[F, T]): DecodeResult[F, T] =
    decoder.decode(this, strict = false)

  /** Remove headers that satisfy the predicate
    *
    * @param f predicate
    * @return a new message object which lacks the specified headers
    */
  final def filterHeaders(f: Header => Boolean)(implicit F: Functor[F]): M[F] =
    transformHeaders(_.filter(f))

  /** Generates a new message object with the specified key/value pair appended to the [[org.http4s.AttributeMap]]
    *
    * @param key [[AttributeKey]] with which to associate the value
    * @param value value associated with the key
    * @tparam A type of the value to store
    * @return a new message object with the key/value pair appended
    */
  def withAttribute[A](key: AttributeKey[A], value: A)(implicit F: Functor[F]): M[F] =
    change(body, headers, attributes.put(key, value))

  /** Generates a new message object with the specified key/value pair appended to the [[org.http4s.AttributeMap]]
    *
    * @param entry [[AttributeEntry]] entry to add
    * @tparam V type of the value to store
    * @return a new message object with the key/value pair appended
    */
  def withAttribute[V](entry: AttributeEntry[V])(implicit F: Functor[F]): M[F] =
    withAttribute(entry.key, entry.value)

  def transformHeaders(f: Headers => Headers)(implicit F: Functor[F]): M[F]

  /** Added the [[org.http4s.headers.Content-Type]] header to the response */
  final def withType(t: MediaType)(implicit F: Functor[F]): M[F] =
    putHeaders(`Content-Type`(t))

  final def withContentType(contentType: Option[`Content-Type`])(implicit F: Functor[F]): M[F] =
    contentType match {
      case Some(t) => putHeaders(t)
      case None => filterHeaders(_.is(`Content-Type`))
    }

  final def removeHeader(key: HeaderKey)(implicit F: Functor[F]): M[F] = filterHeaders(_.isNot(key))

  /** Replaces the [[Header]]s of the incoming Request object
    *
    * @param headers [[Headers]] containing the desired headers
    * @return a new Request object
    */
  final def replaceAllHeaders(headers: Headers)(implicit F: Functor[F]): M[F] =
    transformHeaders(_ => headers)

  /** Replace the existing headers with those provided */
  final def replaceAllHeaders(headers: Header*)(implicit F: Functor[F]): M[F] =
    replaceAllHeaders(Headers(headers.toList))

  /** Add the provided headers to the existing headers, replacing those of the same header name
    * The passed headers are assumed to contain no duplicate Singleton headers.
    */
  final def putHeaders(headers: Header*)(implicit F: Functor[F]): M[F] =
    transformHeaders(_.put(headers: _*))

  final def withTrailerHeaders(trailerHeaders: F[Headers])(implicit F: Functor[F]): M[F] =
    withAttribute(Message.Keys.TrailerHeaders[F], trailerHeaders)

  /** Decode the [[Message]] to the specified type
    *
    * @param decoder [[EntityDecoder]] used to decode the [[Message]]
    * @tparam T type of the result
    * @return the effect which will generate the `DecodeResult[T]`
    */
  def attemptAs[T](implicit F: FlatMap[F], decoder: EntityDecoder[F, T]): DecodeResult[F, T]

  /** Decode the [[Message]] to the specified type
    *
    * If no valid [[Status]] has been described, allow Ok
    * @param decoder [[EntityDecoder]] used to decode the [[Message]]
    * @tparam T type of the result
    * @return the effect which will generate the T
    */
  final def as[T](implicit F: FlatMap[F], decoder: EntityDecoder[F, T]): F[T] =
    attemptAs.fold(throw _, identity)

}

object Message {
  private[http4s] val logger = getLogger
  object Keys {
    private[this] val trailerHeaders: AttributeKey[Any] = AttributeKey[Any]
    def TrailerHeaders[F[_]]: AttributeKey[F[Headers]] =
      trailerHeaders.asInstanceOf[AttributeKey[F[Headers]]]
  }

}
