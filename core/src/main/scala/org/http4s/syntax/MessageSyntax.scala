package org.http4s.syntax

import org.http4s._
import org.http4s.headers.`Content-Type`
import io.chrisdavenport.vault._
import cats._


trait MessageSyntax{
  implicit class MessageOps[M[_[_]], F[_]](private val m: M[F])(implicit M: Message[M]){

    def httpVersion: HttpVersion = M.httpVersion(m)

    def body: EntityBody[F] = M.body(m)

    def withHttpVersion(httpVersion: HttpVersion): M[F] =
      M.withHttpVersion(m, httpVersion)

    def withHeaders(headers: Headers): M[F] =
      M.withHeaders(m, headers)

    def withHeaders(headers: Header*): M[F] =
      M.withHeaders(m, headers:_*)

    def withAttributes(attributes: Vault): M[F] =
      M.withAttributes(m, attributes)

    // Body methods

    /** Replace the body of this message with a new body
      *
      * @param b body to attach to this method
      * @param w [[EntityEncoder]] with which to convert the body to an [[EntityBody]]
      * @tparam T type of the Body
      * @return a new message with the new body
      */
    def withEntity[T](b: T)(implicit w: EntityEncoder[F, T]): M[F] = M.withEntity(m, b)

    /** Sets the entity body without affecting headers such as `Transfer-Encoding`
      * or `Content-Length`. Most use cases are better served by [[withEntity]],
      * which uses an [[EntityEncoder]] to maintain the headers.
      */
    def withBodyStream(body: EntityBody[F]): M[F] =
      M.withBodyStream(m, body)

    /** Set an empty entity body on this message, and remove all payload headers
      * that make no sense with an empty body.
      */
    def withEmptyBody: M[F] =
      withBodyStream(EmptyBody).transformHeaders(_.removePayloadHeaders)

    // General header methods

    def transformHeaders(f: Headers => Headers): M[F] =
      M.transformHeaders(m, f)

    /** Keep headers that satisfy the predicate
      *
      * @param f predicate
      * @return a new message object which has only headers that satisfy the predicate
      */
    def filterHeaders(f: Header => Boolean): M[F] =
      M.filterHeaders(m, f)

    def removeHeader(key: HeaderKey): M[F] =
      M.removeHeader(m, key)

    /** Add the provided headers to the existing headers, replacing those of the same header name
      * The passed headers are assumed to contain no duplicate Singleton headers.
      */
    def putHeaders(headers: Header*): M[F] =
      M.putHeaders(m, headers:_*)

    def withTrailerHeaders(trailerHeaders: F[Headers]): M[F] =
      M.withTrailerHeaders(m, trailerHeaders)

    def withoutTrailerHeaders: M[F] =
      M.withoutTrailerHeaders(m)

    /**
      * The trailer headers, as specified in Section 3.6.1 of RFC 2616. The resulting
      * F might not complete until the entire body has been consumed.
      */
    def trailerHeaders(implicit F: Applicative[F]): F[Headers] =
      M.trailerHeaders(m)

    // Specific header methods
    def withContentType(contentType: `Content-Type`): M[F] =
      M.withContentType(m, contentType)

    def withoutContentType: M[F] =
      M.withoutContentType(m)

    def withContentTypeOption(contentTypeO: Option[`Content-Type`]): M[F] =
      M.withContentTypeOption(m, contentTypeO)

    def isChunked: Boolean =
      M.isChunked(m)

    // Attribute methods

    /** Generates a new message object with the specified key/value pair appended to the [[AttributeMap]]
      *
      * @param key [[Key]] with which to associate the value
      * @param value value associated with the key
      * @tparam A type of the value to store
      * @return a new message object with the key/value pair appended
      */
    def withAttribute[A](key: Key[A], value: A): M[F] =
      M.withAttribute(m, key, value)

    /**
      * Returns a new message object without the specified key in the [[AttributeMap]]
      *
      * @param key [[Key]] to remove
      * @return a new message object without the key
      */
    def withoutAttribute(key: Key[_]): M[F] =
      M.withoutAttribute(m, key)
  }
}