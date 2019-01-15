package org.http4s.server.middleware

import cats.Applicative
import cats.data.Kleisli
import cats.implicits._
import org.http4s.util.CaseInsensitiveString
import org.http4s.{
  AttributeKey,
  Header,
  Http,
  HttpApp,
  Method,
  ParseResult,
  Request,
  Response,
  Status
}

object HttpMethodOverrider {

  /**
    * HttpMethodOverrider middleware config options.
    */
  class HttpMethodOverriderConfig(
      val overrideStrategy: OverrideStrategy,
      val overridableMethods: Set[Method]) {

    type Self = HttpMethodOverriderConfig

    private def copy(
        overrideStrategy: OverrideStrategy = overrideStrategy,
        overridableMethods: Set[Method] = overridableMethods
    ): Self =
      new HttpMethodOverriderConfig(overrideStrategy, overridableMethods)

    def withOverrideStrategy(overrideStrategy: OverrideStrategy): Self =
      copy(overrideStrategy = overrideStrategy)

    def withOverridableMethods(overridableMethods: Set[Method]): Self =
      copy(overridableMethods = overridableMethods)
  }

  object HttpMethodOverriderConfig {
    def apply(
        overrideStrategy: OverrideStrategy,
        overridableMethods: Set[Method]): HttpMethodOverriderConfig =
      new HttpMethodOverriderConfig(overrideStrategy, overridableMethods)
  }

  sealed trait OverrideStrategy
  final case class HeaderOverrideStrategy(headerName: CaseInsensitiveString)
      extends OverrideStrategy
  final case class QueryOverrideStrategy(paramName: String) extends OverrideStrategy
  // TODO: tory to fit this inside the main logic
  //  final case class FunctionOverrideStrategy[G[_]](fn: Request[G] => Method) extends OverrideStrategy

  val defaultConfig = HttpMethodOverriderConfig(
    HeaderOverrideStrategy(CaseInsensitiveString("X-HTTP-Method-Override")),
    Set(Method.POST))

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
  def apply[F[_], G[_]](http: Http[F, G], config: HttpMethodOverriderConfig)(
      implicit F: Applicative[F]): Http[F, G] = {

    def processRequestWithMethod(
        req: Request[G],
        parseResult: ParseResult[Method]): F[Response[G]] = parseResult match {
      case Left(_) => F.pure(Response[G](Status.BadRequest))
      case Right(om) => http(updateRequestWithMethod(req, om)).map(updateVaryHeader)
    }

    def updateVaryHeader(resp: Response[G]): Response[G] = {
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

    def updateRequestWithMethod(req: Request[G], om: Method): Request[G] = {
      val attrs = req.attributes ++ Seq(overriddenMethodAttrKey(req.method))
      req.withAttributes(attrs).withMethod(om)
    }

    def ignoresOverrideIfNotAllowed(req: Request[G]): Option[Unit] =
      config.overridableMethods.contains(req.method).guard[Option].as(())

    def parseMethod(m: String): ParseResult[Method] = Method.fromString(m.toUpperCase)

    def getUnsafeOverrideMethod(req: Request[G]): Option[String] =
      config.overrideStrategy match {
        case HeaderOverrideStrategy(headerName) => req.headers.get(headerName).map(_.value)
        case QueryOverrideStrategy(parameter) => req.params.get(parameter)
      }

    Kleisli { req: Request[G] =>
      {
        (ignoresOverrideIfNotAllowed(req) *> getUnsafeOverrideMethod(req))
          .map(parseMethod)
          .map(processRequestWithMethod(req, _))
          .getOrElse(http(req))
      }
    }
  }
}
