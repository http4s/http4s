package org.http4s

import io.chrisdavenport.vault._
import cats._
import cats.implicits._
import org.http4s.headers.{`Content-Type`, `Set-Cookie`}
import fs2._
import fs2.text._

/** Representation of the HTTP response to send back to the client
  *
  * @param status [[Status]] code and message
  * @param headers [[Headers]] containing all response headers
  * @param body EntityBody[F] representing the possible body of the response
  * @param attributes [[Vault]] containing additional parameters which may be used by the http4s
  *                   backend for additional processing such as java.io.File object
  */
final case class Response[F[_]](
    status: Status = Status.Ok,
    httpVersion: HttpVersion = HttpVersion.`HTTP/1.1`,
    headers: Headers = Headers.empty,
    body: EntityBody[F] = EmptyBody,
    attributes: Vault = Vault.empty){

  def mapK[G[_]](f: F ~> G): Response[G] = Response[G](
    status = status,
    httpVersion = httpVersion,
    headers = headers,
    body = body.translate(f),
    attributes = attributes
  )

  def withStatus(status: Status): Response[F] =
    copy(status = status)

  def change(
      httpVersion: HttpVersion,
      body: EntityBody[F],
      headers: Headers,
      attributes: Vault
  ): Response[F] =
    copy(
      httpVersion = httpVersion,
      body = body,
      headers = headers,
      attributes = attributes
    )

  /** Add a Set-Cookie header for the provided [[ResponseCookie]] */
  def addCookie(cookie: ResponseCookie): Response[F] =
    Response.message.putHeaders(this, `Set-Cookie`(cookie))

  /** Add a [[`Set-Cookie`]] header with the provided values */
  def addCookie(name: String, content: String, expires: Option[HttpDate] = None): Response[F] =
    addCookie(ResponseCookie(name, content, expires))

  /** Add a [[`Set-Cookie`]] which will remove the specified cookie from the client */
  def removeCookie(cookie: ResponseCookie): Response[F] =
    Response.message.putHeaders(this, cookie.clearCookie)

  /** Add a [[`Set-Cookie`]] which will remove the specified cookie from the client */
  def removeCookie(name: String): Response[F] =
    Response.message.putHeaders(this, ResponseCookie(name, "").clearCookie)

  /** Returns a list of cookies from the [[`Set-Cookie`]]
    * headers. Includes expired cookies, such as those that represent cookie
    * deletion. */
  def cookies: List[ResponseCookie] =
    `Set-Cookie`.from(headers).map(_.cookie)

  override def toString: String =
    s"""Response(status=${status.code}, headers=${headers.redactSensitive()})"""
}

object Response {
  private[this] val pureNotFound: Response[Pure] =
    Response(
      Status.NotFound,
      body = Stream("Not found").through(utf8Encode),
      headers = Headers(`Content-Type`(MediaType.text.plain, Charset.`UTF-8`) :: Nil))

  implicit val message: Message[Response] = new Message[Response]{
    def httpVersion[F[_]](m: Response[F]): HttpVersion = m.httpVersion
    def headers[F[_]](m: Response[F]): Headers = m.headers
    def body[F[_]](m: Response[F]): org.http4s.EntityBody[F] = m.body
    def attributes[F[_]](m: Response[F]): Vault = m.attributes
    def change[F[_]](m: Response[F])(httpVersion: HttpVersion, body: org.http4s.EntityBody[F], headers: Headers, attributes: Vault): Response[F] = 
      m.change(httpVersion, body, headers, attributes)
  }

  def notFound[F[_]]: Response[F] = pureNotFound.copy(body = pureNotFound.body.covary[F])

  def notFoundFor[F[_]: Applicative](request: Request[F])(
      implicit encoder: EntityEncoder[F, String]): F[Response[F]] =
      Response.message.withEntity(
        Response[F](Status.NotFound),
        s"${request.pathInfo} not found"
      ).pure[F]

  def timeout[F[_]]: Response[F] =
    Response.message.withEntity(
      Response[F](Status.ServiceUnavailable),
      "Response timed out"
    )
}
