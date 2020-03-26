package org.http4s

import cats._
import cats.implicits._
import cats.effect.IO
import org.http4s.headers._
import org.log4s.getLogger
import _root_.io.chrisdavenport.vault._


/**
  * Represents a HTTP Message. The interesting subclasses are Request and Response.
  */
trait Message[M[_[_]]] extends Media[M]{ self =>
  def httpVersion[F[_]](m: M[F]): HttpVersion

  def headers[F[_]](m: M[F]): Headers

  def body[F[_]](m: M[F]): EntityBody[F]

  def attributes[F[_]](m: M[F]): Vault

  protected def change[F[_]](m: M[F])(
      httpVersion: HttpVersion = self.httpVersion(m),
      body: EntityBody[F] = self.body(m),
      headers: Headers = self.headers(m),
      attributes: Vault = self.attributes(m)
  ): M[F]

  // Concretes
  def withHttpVersion[F[_]](m: M[F], httpVersion: HttpVersion): M[F] =
    change(m)(httpVersion = httpVersion)

  def withHeaders[F[_]](m: M[F], headers: Headers): M[F] =
    change(m)(headers = headers)

  def withHeaders[F[_]](m: M[F], headers: Header*): M[F] =
    withHeaders(m, Headers(headers.toList))

  def withAttributes[F[_]](m: M[F], attributes: Vault): M[F] =
    change(m)(attributes = attributes)

  // Body Methods

  /** Replace the body of this message with a new body
    *
    * @param b body to attach to this method
    * @param w [[EntityEncoder]] with which to convert the body to an [[EntityBody]]
    * @tparam T type of the Body
    * @return a new message with the new body
    */
  def withEntity[F[_], T](m: M[F], b: T)(implicit w: EntityEncoder[F, T]): M[F] = {
    val entity = w.toEntity(b)
    val hs = entity.length match {
      case Some(l) =>
        `Content-Length`
          .fromLong(l)
          .fold[Headers](_ => {
            Message.logger.warn(s"Attempt to provide a negative content length of $l")
            w.headers
          }, cl => Headers(cl :: w.headers.toList))
      case None => w.headers
    }
    change(m)(body = entity.body, headers = self.headers(m) ++ hs)
  }

  /** Sets the entity body without affecting headers such as `Transfer-Encoding`
    * or `Content-Length`. Most use cases are better served by [[withEntity]],
    * which uses an [[EntityEncoder]] to maintain the headers.
    */
  def withBodyStream[F[_]](m: M[F], body: EntityBody[F]): M[F] =
    change(m)(body = body)

  /** Set an empty entity body on this message, and remove all payload headers
    * that make no sense with an empty body.
    */
  def withEmptyBody[F[_]](m: M[F]): M[F] =
    transformHeaders(withBodyStream(m, EmptyBody), _.removePayloadHeaders)


  // General header methods

  def transformHeaders[F[_]](m: M[F], f: Headers => Headers): M[F] =
    change(m)(headers = f(headers(m)))

  /** Keep headers that satisfy the predicate
    *
    * @param f predicate
    * @return a new message object which has only headers that satisfy the predicate
    */
  def filterHeaders[F[_]](m: M[F], f: Header => Boolean): M[F] =
    transformHeaders(m, _.filter(f))

  def removeHeader[F[_]](m: M[F], key: HeaderKey): M[F] =
    filterHeaders(m, _.isNot(key))

  /** Add the provided headers to the existing headers, replacing those of the same header name
    * The passed headers are assumed to contain no duplicate Singleton headers.
    */
  def putHeaders[F[_]](m: M[F], headers: Header*): M[F] =
    transformHeaders(m, _.put(headers: _*))

  def withTrailerHeaders[F[_]](m: M[F], trailerHeaders: F[Headers]): M[F] =
    withAttribute(m, Message.Keys.TrailerHeaders[F], trailerHeaders)

  def withoutTrailerHeaders[F[_]](m: M[F]): M[F] =
    withoutAttribute(m, Message.Keys.TrailerHeaders[F])

  /**
    * The trailer headers, as specified in Section 3.6.1 of RFC 2616. The resulting
    * F might not complete until the entire body has been consumed.
    */
  def trailerHeaders[F[_]](m: M[F])(implicit F: Applicative[F]): F[Headers] =
    attributes(m).lookup(Message.Keys.TrailerHeaders[F]).getOrElse(F.pure(Headers.empty))

  // Specific header methods

  def withContentType[F[_]](m: M[F], contentType: `Content-Type`): M[F] =
    putHeaders(m, contentType)

  def withoutContentType[F[_]](m: M[F]): M[F] =
    filterHeaders(m, _.isNot(`Content-Type`))

  def withContentTypeOption[F[_]](m: M[F], contentTypeO: Option[`Content-Type`]): M[F] =
    contentTypeO.fold(withoutContentType(m))(withContentType(m, _))

  def isChunked[F[_]](m: M[F]): Boolean =
    headers(m).get(`Transfer-Encoding`).exists(_.values.contains_(TransferCoding.chunked))

  // Attribute methods

  /** Generates a new message object with the specified key/value pair appended to the [[AttributeMap]]
    *
    * @param key [[Key]] with which to associate the value
    * @param value value associated with the key
    * @tparam A type of the value to store
    * @return a new message object with the key/value pair appended
    */
  def withAttribute[F[_], A](m: M[F], key: Key[A], value: A): M[F] =
    change(m)(attributes = attributes(m).insert(key, value))

  /**
    * Returns a new message object without the specified key in the [[AttributeMap]]
    *
    * @param key [[Key]] to remove
    * @return a new message object without the key
    */
  def withoutAttribute[F[_]](m: M[F], key: Key[_]): M[F] =
    change(m)(attributes = attributes(m).delete(key))
}

object Message {
  def apply[M[_[_]]](implicit ev: Message[M]): ev.type = ev

  private[http4s] val logger = getLogger
  object Keys {
    private[this] val trailerHeaders: Key[Any] = Key.newKey[IO, Any].unsafeRunSync
    def TrailerHeaders[F[_]]: Key[F[Headers]] = trailerHeaders.asInstanceOf[Key[F[Headers]]]
  }
}



