package org.http4s

import cats._
import cats.implicits._
import fs2._
import fs2.text._
import org.http4s.headers._
import org.log4s.getLogger

trait Message[M[_[_]], F[_]] {

  def httpVersion(m: M[F]): HttpVersion

  def headers(m: M[F]): Headers

  def body(m: M[F]): EntityBody[F]

  def attributes(m: M[F]): AttributeMap

  def change(m: M[F])(
    body: EntityBody[F],
    headers: Headers,
    attributes: AttributeMap
  ): M[F]

  /** Replace the body of this message with a new body
    *
    * @param b body to attach to this method
    * @param w [[EntityEncoder]] with which to convert the body to an [[EntityBody]]
    * @tparam T type of the Body
    * @return a new message with the new body
    */
  def withBody[T](m: M[F])(b: T)(implicit F: Monad[F], w: EntityEncoder[F, T]): F[M[F]] =
    w.toEntity(b).flatMap { entity =>
        val headersF: F[List[Header]] = entity.length match {
          case Some(l) =>
            `Content-Length`
              .fromLong(l) match {
              case Right(header) => (header :: w.headers.toList).pure[F]
              case Left(_) => F.pure {
                Message.logger.warn(s"Attempt to provide a negative content length of $l")
                w.headers.toList
              }
            }
          case None => F.pure(w.headers.toList)
        }
      headersF.map(newHeaders => change(m)(body(m), newHeaders ++ headers(m), attributes(m)))
    }


  def withBodyStream(m: M[F])(body: EntityBody[F]) : M[F] =
    change(m)(body, headers(m), attributes(m))

  def contentType(m: M[F]): Option[`Content-Type`] = headers(m).get(`Content-Type`)

  def charset(m: M[F]) : Option[Charset] = contentType(m).flatMap(_.charset)

  def bodyAsText(m: M[F])(implicit defaultCharset: Charset = DefaultCharset) : Stream[F, String] = {
    charset(m).getOrElse(defaultCharset) match {
      case Charset.`UTF-8` => body(m).through(utf8Decode)
      case cs => body(m).through(util.decode(cs))
    }
  }

  def transformHeaders(m: M[F])(f: Headers => Headers): M[F] =
    change(m)(body(m), f(headers(m)), attributes(m))

  def replaceAllHeaders(m: M[F])(headers: Headers): M[F] =
    change(m)(body(m), headers, attributes(m))

  def replaceAllHeadersWith(m: M[F])(headers: Header*): M[F] =
    replaceAllHeaders(m)(Headers(headers.toList))

  def withAttribute[A](m: M[F])(key: AttributeKey[A], value: A): M[F] =
    change(m)(body(m), headers(m), attributes(m).put(key, value))

  def withEmptyBody(m: M[F]): M[F] =
    transformHeaders(withBodyStream(m)(EmptyBody.covary[F]))(_.removePayloadHeaders)

  def contentLength(m: M[F]): Option[Long] =
    headers(m).get(`Content-Length`).map(_.length)

  def isChunked(m: M[F]): Boolean = headers(m).get(`Transfer-Encoding`).exists{ te =>
      val nel = te.values
      if (nel.head == TransferCoding.chunked) true
      else nel.tail.contains(TransferCoding.chunked)
  }

  def trailerHeaders(m: M[F])(implicit F: Applicative[F]): F[Headers] =
    attributes(m).get(Message.Keys.TrailerHeaders[F]).getOrElse(Headers.empty.pure[F])

  def filterHeaders(m: M[F])(f: Header => Boolean): M[F] =
    transformHeaders(m)(_.filter(f))

  def withAttributeEntry[V](m: M[F])(entry: AttributeEntry[V]): M[F] =
    withAttribute(m)(entry.key, entry.value)

  def putHeaders(m: M[F])(headers: Header*): M[F] =
    transformHeaders(m)(_.put(headers:_*))

  def withType(m: M[F])(t: MediaType): M[F] =
    putHeaders(m)(`Content-Type`(t))

  def withContentType(m: M[F])(contentType: `Content-Type`): M[F] =
    putHeaders(m)(contentType)

  def withoutContentType(m: M[F]): M[F] =
    filterHeaders(m)(_.is(`Content-Type`))

  def withContentTypeOption(m: M[F])(contentTypeO: Option[`Content-Type`]): M[F] =
    contentTypeO.fold(withoutContentType(m))(withContentType(m))

  def removeHeader(m: M[F])(key: HeaderKey): M[F] =
    filterHeaders(m)(_.isNot(key))

  /** Decode the [[Message]] to the specified type
    *
    * @param decoder [[EntityDecoder]] used to decode the [[Message]]
    * @tparam T type of the result
    * @return the effect which will generate the `DecodeResult[T]`
    */
  def attemptAs[T](m: M[F])(implicit decoder: EntityDecoder[F, T]): DecodeResult[F, T] = {
    decoder.decode(m, strict = false)(this)
  }


  /** Decode the [[Message]] to the specified type
    *
    * If no valid [[Status]] has been described, allow Ok
    * @param decoder [[EntityDecoder]] used to decode the [[Message]]
    * @tparam T type of the result
    * @return the effect which will generate the T
    */
  final def as[T](m: M[F])(implicit F: Functor[F], decoder: EntityDecoder[F, T]): F[T] =
    attemptAs(m).fold(throw _, identity)

}


object Message {
  private[http4s] val logger = getLogger

  def apply[M[_[_]], F[_]](implicit mess: Message[M, F]): Message[M, F] = mess

  object messSyntax {
    implicit class messOps[M[_[_]], F[_]](m: M[F])(implicit mess: Message[M, F]){
      def httpVersion: HttpVersion = mess.httpVersion(m)
      def headers: Headers = mess.headers(m)
      def body: EntityBody[F] = mess.body(m)
      def attributes: AttributeMap = mess.attributes(m)
      def change(body: EntityBody[F], headers: Headers, attributes: AttributeMap): M[F] =
        mess.change(m)(body, headers, attributes)
      def withBody[T](b: T)(implicit F: Monad[F], w: EntityEncoder[F, T]): F[M[F]] = mess.withBody(m)(b)(F, w)
      def withBodyStream(body: EntityBody[F]): M[F] = mess.withBodyStream(m)(body)
      def contentType: Option[`Content-Type`] = mess.contentType(m)
      def charset: Option[Charset] = mess.charset(m)
      def bodyAsText(implicit defaultCharset: Charset = DefaultCharset): Stream[F, String] = mess.bodyAsText(m)(defaultCharset)
      def transformHeaders(f: Headers => Headers): M[F] = mess.transformHeaders(m)(f)
      def replaceAllHeaders(headers: Headers): M[F] = mess.replaceAllHeaders(m)(headers)
      def replaceAllHeadersWith(headers: Header*): M[F] = mess.replaceAllHeadersWith(m)(headers:_*)
      def withAttribute[A](attributeKey: AttributeKey[A], value: A): M[F] = mess.withAttribute[A](m)(attributeKey, value)
      def withEmptyBody: M[F] = mess.withEmptyBody(m)
      def contentLength: Option[Long] = mess.contentLength(m)
      def isChunked : Boolean = mess.isChunked(m)
      def trailerHeaders(implicit F: Applicative[F]): F[Headers] = mess.trailerHeaders(m)
      def filterHeaders(f: Header => Boolean): M[F] = mess.filterHeaders(m)(f)
      def withAttributeEntry[V](attributeEntry: AttributeEntry[V]): M[F] = mess.withAttributeEntry[V](m)(attributeEntry)
      def putHeaders(header: Header*): M[F] = mess.putHeaders(m)(header:_*)
      def withType(t: MediaType): M[F] = mess.withType(m)(t)
      def withContentType(contentType: `Content-Type`): M[F] = mess.withContentType(m)(contentType)
      def withoutContentType: M[F] = mess.withoutContentType(m)
      def withContentTypeOption(contentTypeO: Option[`Content-Type`]): M[F] = mess.withContentTypeOption(m)(contentTypeO)
      def removeHeader(key: HeaderKey): M[F] = mess.removeHeader(m)(key)

      def attemptAs[T](implicit decoder: EntityDecoder[F, T]): DecodeResult[F, T] = mess.attemptAs[T](m)(decoder)
      def as[T](implicit F: Functor[F], decoder: EntityDecoder[F, T]): F[T] = mess.as[T](m)(F, decoder)
    }
  }

  object Keys {
    private[this] val trailerHeaders: AttributeKey[Any] = AttributeKey[Any]
    def TrailerHeaders[F[_]]: AttributeKey[F[Headers]] =
      trailerHeaders.asInstanceOf[AttributeKey[F[Headers]]]
  }

}