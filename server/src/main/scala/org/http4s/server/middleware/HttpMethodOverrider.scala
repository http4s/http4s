package org.http4s.server.middleware

import cats.Applicative
import cats.data.Kleisli
import cats.implicits._
import org.http4s.util.CaseInsensitiveString
import org.http4s.{AttributeKey, Header, HttpApp, Method, ParseResult, Request, Response, Status}

object HttpMethodOverrider {

  /**
    * HttpMethodOverrider middleware config options.
    */
  final case class HttpMethodOverriderConfig(
      overrideStrategy: OverrideStrategy,
      overridableMethods: List[Method])

  sealed trait OverrideStrategy
  final case class HeaderOverrideStrategy(headerName: CaseInsensitiveString)
      extends OverrideStrategy
  final case class QueryOverrideStrategy(paramName: String) extends OverrideStrategy

  val defaultConfig = HttpMethodOverriderConfig(
    HeaderOverrideStrategy(CaseInsensitiveString("X-HTTP-Method-Override")),
    List(Method.POST))

  val overriddenMethodAttrKey: AttributeKey[Method] = AttributeKey[Method]

  /** Simple middleware for HTTP Method Override.
    *
    * This middleware lets you use  HTTP verbs such as PUT or DELETE in places where the client
    * doesn't support it. Camouflage your request with another HTTP verb(usually POST) and sneak
    * the desired one using a custom header or request parameter. The middleware will '''override'''
    * the original verb with the new one for you, allowing the request the be dispatched properly.
    *
    * @param http [[HttpApp]] to transform
    * @param config http method overrider config
    */
  def apply[F[_]](http: HttpApp[F], config: HttpMethodOverriderConfig)(
      implicit F: Applicative[F]): HttpApp[F] = {

    def processRequestWithMethod(
        req: Request[F],
        parseResult: ParseResult[Method]): F[Response[F]] = parseResult match {
      case Left(_) => F.pure(Response[F](Status.MethodNotAllowed))
      case Right(om) => http(updateRequestWithMethod(req, om)).map(updateVaryHeader)
    }

    def updateVaryHeader(resp: Response[F]): Response[F] = {
      val varyHeaderName = CaseInsensitiveString("Vary")
      config.overrideStrategy match {
        case HeaderOverrideStrategy(headerName) =>
          val updatedVaryHeader =
            resp.headers
              .get(varyHeaderName)
              .map((h: Header) => Header(h.name.value, s"${h.value}, ${headerName.value}"))
              .getOrElse(Header(varyHeaderName.value, headerName.value))

          resp.withHeaders(resp.headers.put(updatedVaryHeader))
        case QueryOverrideStrategy(_) => resp
      }
    }

    def updateRequestWithMethod(req: Request[F], om: Method): Request[F] = {
      val attrs = req.attributes ++ Seq(overriddenMethodAttrKey(req.method))
      req.withAttributes(attrs).withMethod(om)
    }

    def ignoresOverrideIfNotAllowed(req: Request[F]): Option[Unit] =
      if (config.overridableMethods.contains(req.method)) Some(()) else None

    def getUnsafeOverrideMethod(req: Request[F]): Option[String] =
      config.overrideStrategy match {
        case HeaderOverrideStrategy(headerName) => req.headers.get(headerName).map(_.value)
        case QueryOverrideStrategy(parameter) => req.params.get(parameter)
      }

    Kleisli { req: Request[F] =>
      {
        (ignoresOverrideIfNotAllowed(req) *> getUnsafeOverrideMethod(req))
          .map(m => Method.fromString(m.toUpperCase))
          .map(processRequestWithMethod(req, _))
          .getOrElse(http(req))
      }
    }
  }
}
