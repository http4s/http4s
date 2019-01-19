package org.http4s.server.middleware

import cats.data.Kleisli
import cats.effect.Sync
import cats.implicits._
import cats.{Monad, ~>}
import org.http4s.util.CaseInsensitiveString
import org.http4s.{
  AttributeKey,
  Header,
  Http,
  Method,
  ParseResult,
  Request,
  Response,
  Status,
  UrlForm
}

import scala.reflect.runtime.universe._

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
  final case class FormOverrideStrategy[G[_], F[_]](
      fieldName: String,
      naturalTransformation: G ~> F)
      extends OverrideStrategy

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
    * @param http [[Http]] to transform
    * @param config http method overrider config
    */
  def apply[F[_], G[_]](http: Http[F, G], config: HttpMethodOverriderConfig)(
      implicit F: Monad[F],
      S: Sync[G],
      TT: TypeTag[G ~> F]): Http[F, G] = {

    lazy val runtimeTypeNT = implicitly[TypeTag[G ~> F]].tpe

    val parseMethod = (m: String) => Method.fromString(m.toUpperCase)

    val processRequestWithOriginalMethod = (req: Request[G]) => http(req)

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
        case _ => resp
      }
    }

    def updateRequestWithMethod(req: Request[G], om: Method): Request[G] = {
      val attrs = req.attributes ++ Seq(overriddenMethodAttrKey(req.method))
      req.withAttributes(attrs).withMethod(om)
    }

    def getUnsafeOverrideMethod(req: Request[G]): F[Option[String]] =
      config.overrideStrategy match {
        case HeaderOverrideStrategy(headerName) => F.pure(req.headers.get(headerName).map(_.value))
        case QueryOverrideStrategy(parameter) => F.pure(req.params.get(parameter))
        case FormOverrideStrategy(field, f) if runtimeTypeNT == typeOf[G ~> F] =>
          val nt = f.asInstanceOf[G ~> F]
          for {
            formFields <- nt(
              UrlForm
                .entityDecoder[G]
                .decode(req, strict = true)
                .value
                .map(_.toOption.map(_.values)))

          } yield formFields.flatMap(_.get(field).flatMap(_.uncons.map(_._1)))
      }

    def processRequest(req: Request[G]): F[Response[G]] = getUnsafeOverrideMethod(req).flatMap {
      case Some(m: String) => parseMethod.andThen(processRequestWithMethod(req, _)).apply(m)
      case None => processRequestWithOriginalMethod(req)
    }

    Kleisli { req: Request[G] =>
      {
        config.overridableMethods
          .contains(req.method)
          .guard[Option]
          .as(processRequest(req))
          .getOrElse(processRequestWithOriginalMethod(req))
      }
    }
  }
}
