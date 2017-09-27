package org.http4s

import cats.{Applicative, Functor, Monad, Monoid}
import cats.implicits._
import fs2.Stream
import org.http4s.headers.`Set-Cookie`
import org.http4s._

/** Represents that a service either returns a [[Response]] or a [[Pass]] to
  * fall through to another service.
  */
sealed trait MaybeResponse[F[_]] {
  def cata[A](f: Response[F] => A, a: => A): A =
    this match {
      case r: Response[F] => f(r)
      case _: Pass[F] => a
    }

  def orElse[B >: Response[F]](b: => B): B =
    this match {
      case r: Response[F] => r
      case _: Pass[F] => b
    }

  def orNotFound: Response[F] =
    orElse(Response(Status.NotFound))

  def toOption: Option[Response[F]] =
    cata(Some(_), None)
}

object MaybeResponse extends MaybeResponseInstances {
  // Will probably need to be imported into Case Classes for Availability. Or Each will Implement their own.
  def MaybeResponseMessage[F]: Message[F, MaybeResponse[F]] = ???
}

trait MaybeResponseInstances {
  implicit def http4sMonoidForMaybeResponse[F[_]]: Monoid[MaybeResponse[F]] =
    new Monoid[MaybeResponse[F]] {
      def empty =
        Pass()
      def combine(a: MaybeResponse[F], b: MaybeResponse[F]) =
        a.orElse(b)
    }

  implicit def http4sMonoidForFMaybeResponse[F[_]](
      implicit F: Monad[F]): Monoid[F[MaybeResponse[F]]] =
    new Monoid[F[MaybeResponse[F]]] {
      override def empty =
        Pass.pure[F]

      override def combine(fa: F[MaybeResponse[F]], fb: F[MaybeResponse[F]]) =
        fa.flatMap(_.cata(F.pure, fb))
    }
}

final case class Pass[F[_]]() extends MaybeResponse[F]

object Pass {
  def pure[F[_]](implicit F: Applicative[F]): F[MaybeResponse[F]] = F.pure(Pass[F]())
}

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
    attributes: AttributeMap = AttributeMap.empty)
    extends MaybeResponse[F] {

  def withStatus(status: Status)(implicit F: Functor[F]): Response[F] =
    copy(status = status)

  def withBodyStream(body: EntityBody[F]): Response[F] =
    copy(body = body)

  protected def change(
      body: EntityBody[F],
      headers: Headers,
      attributes: AttributeMap): Response[F] =
    copy(body = body, headers = headers, attributes = attributes)

  override def toString: String = {
    val newHeaders = headers.redactSensitive()
    s"""Response(status=${status.code}, headers=$newHeaders)"""
  }

  def asMaybeResponse: MaybeResponse[F] = this

  /** Returns a list of cookies from the [[org.http4s.headers.Set-Cookie]]
    * headers. Includes expired cookies, such as those that represent cookie
    * deletion. */
  def cookies: List[Cookie] =
    `Set-Cookie`.from(headers).map(_.cookie)

  /** Add a Set-Cookie header for the provided [[Cookie]] */
  def addCookie(cookie: Cookie)(implicit F: Functor[F], M: Message[F, Response[F]]): Response[F] =
    M.putHeaders(`Set-Cookie`(cookie))

  /** Add a Set-Cookie header with the provided values */
  def addCookie(name: String, content: String, expires: Option[HttpDate] = None)(
      implicit F: Functor[F],
      M: Message[F, Response[F]]): Response[F] =
    addCookie(Cookie(name, content, expires))

  /** Add a [[org.http4s.headers.Set-Cookie]] which will remove the specified cookie from the client */
  def removeCookie(
      cookie: Cookie)(implicit F: Functor[F], M: Message[F, Response[F]]): Response[F] =
    M.putHeaders(cookie.clearCookie)

  /** Add a [[org.http4s.headers.Set-Cookie]] which will remove the specified cookie from the client */
  def removeCookie(name: String)(implicit F: Functor[F], M: Message[F, Response[F]]): Response[F] =
    M.putHeaders(Cookie(name, "").clearCookie)
}

object Response {
  def notFound[F[_]](
      request: Request[F])(implicit F: Monad[F], EE: EntityEncoder[F, String]): Response[F] = {
    val body = s"${request.pathInfo} not found"
    Response[F](Status.NotFound).withBodyStream(Stream.emit(body).through(fs2.text.utf8Encode))
  }
}
