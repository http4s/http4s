package org.http4s

import cats._
import cats.implicits._
import fs2._
import fs2.text._
import org.http4s.headers._

sealed trait Mess[M[_[_]], F[_]] {

  def httpVersion(m: M[F]): HttpVersion

  def headers(m: M[F]): Headers

  def body(m: M[F]): EntityBody[F]

  def attributes(m: M[F]): AttributeMap

  def change(m: M[F])(
    body: EntityBody[F],
    headers: Headers,
    attributes: AttributeMap
  ): M[F]

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

}


object Mess {

  implicit class messOps[M[_[_]], F[_]](m: M[F])(implicit mess: Mess[M, F]){

  }

  def requestInstance[F[_]]: Mess[Request, F] = new Mess[Request, F]{
    override def httpVersion(m: Request[F]): HttpVersion = m.httpVersion
    override def headers(m: Request[F]): Headers = m.headers
    override def body(m: Request[F]): EntityBody[F] = m.body
    override def attributes(m: Request[F]): AttributeMap = m.attributes
    override def change(m: Request[F])(
      body: EntityBody[F],
      headers: Headers,
      attributes: AttributeMap
    ): Request[F] =
      Request[F](
        method = m.method,
        uri = m.uri,
        httpVersion = m.httpVersion,
        headers = headers,
        body = body,
        attributes = attributes
      )
  }

  def responseInstance[F[_]]: Mess[Response, F] = new Mess[Response, F] {
    override def httpVersion(m: Response[F]): HttpVersion = m.httpVersion
    override def headers(m: Response[F]): Headers = m.headers
    override def body(m: Response[F]): EntityBody[F] = m.body
    override def attributes(m: Response[F]): AttributeMap = m.attributes
    override def change(m: Response[F])(
      body: EntityBody[F],
      headers: Headers,
      attributes: AttributeMap
    ): Response[F] = Response[F](
        m.status,
        m.httpVersion,
        headers,
        body,
        attributes
    )
  }

}