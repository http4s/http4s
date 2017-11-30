package org.http4s

import cats.Monad
import fs2.{Pure, Stream, text}
import org.http4s.headers.{`Set-Cookie`, `Content-Type`}
import Message.messSyntax._

/** Representation of the HTTP response to send back to the client
  *
  * @param status [[Status]] code and message
  * @param headers [[Headers]] containing all response headers
  * @param body EntityBody[F] representing the possible body of the response
  * @param attributes [[AttributeMap]] containing additional parameters which may be used by the http4s
  *                   backend for additional processing such as java.io.File object
  */
final case class Response[F[_]](
                                 status: Status = Status.Ok,
                                 httpVersion: HttpVersion = HttpVersion.`HTTP/1.1`,
                                 headers: Headers = Headers.empty,
                                 body: EntityBody[F] = EmptyBody,
                                 attributes: AttributeMap = AttributeMap.empty) {

  import Response._

  /** Change the status of this response object
    *
    * @param status value to replace on the response object
    * @return a new response object with the new status code
    */
  def withStatus(status: Status): Response[F] =
    copy(status = status)

  override def toString: String = {
    val newHeaders = headers.redactSensitive()
    s"""Response(status=${status.code}, headers=$newHeaders)"""
  }

  /** Returns a list of cookies from the [[org.http4s.headers.Set-Cookie]]
    * headers. Includes expired cookies, such as those that represent cookie
    * deletion. */
  def cookies: List[Cookie] =
    `Set-Cookie`.from(headers).map(_.cookie)


  /** Add a Set-Cookie header for the provided [[Cookie]] */
  def addCookie(cookie: Cookie): Response[F] =
    this.putHeaders(`Set-Cookie`(cookie))

  /** Add a Set-Cookie header with the provided values */
  def addCookie(name: String, content: String, expires: Option[HttpDate] = None): Response[F] =
    addCookie(Cookie(name, content, expires))

  /** Add a [[org.http4s.headers.Set-Cookie]] which will remove the specified cookie from the client */
  def removeCookie(cookie: Cookie): Response[F] =
    this.putHeaders(cookie.clearCookie)

  /** Add a [[org.http4s.headers.Set-Cookie]] which will remove the specified cookie from the client */
  def removeCookie(name: String): Response[F] =
    this.putHeaders(Cookie(name, "").clearCookie)
}

object Response {

  private[this] val pureNotFound: Response[Pure] =
    Response(
      Status.NotFound,
      body = Stream("Not found").through(text.utf8Encode),
      headers = Headers(`Content-Type`(MediaType.`text/plain`, Charset.`UTF-8`)))

  def notFound[F[_]]: Response[F] = pureNotFound.copy(body = pureNotFound.body.covary[F])

  def notFoundFor[F[_]: Monad](request: Request[F])(
    implicit encoder: EntityEncoder[F, String]): F[Response[F]] =
    Response[F](Status.NotFound).withBody(s"${request.pathInfo} not found")


  implicit def responseInstance[F[_]]: Message[Response, F] = new Message[Response, F] {
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
