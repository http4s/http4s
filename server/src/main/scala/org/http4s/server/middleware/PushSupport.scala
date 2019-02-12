package org.http4s
package server
package middleware

import cats._
import cats.data.{Kleisli, OptionT}
import cats.effect._
import cats.implicits._
import org.log4s.getLogger
import io.chrisdavenport.vault._

object PushSupport {
  private[this] val logger = getLogger

  implicit def http4sPushOps[F[_]: Functor](response: Response[F]): PushOps[F] =
    new PushOps[F](response)

  final class PushOps[F[_]: Functor](response: Response[F]) extends AnyRef {
    def push(url: String, cascade: Boolean = true)(implicit req: Request[F]): Response[F] = {
      val newUrl = {
        val script = req.scriptName
        if (script.length > 0) {
          val sb = new StringBuilder()
          sb.append(script)
          if (!url.startsWith("/")) sb.append('/')
          sb.append(url).result()
        } else url
      }

      logger.trace(s"Adding push resource: $newUrl")

      val newPushResouces = response.attributes
        .lookup(pushLocationKey)
        .map(_ :+ PushLocation(newUrl, cascade))
        .getOrElse(Vector(PushLocation(newUrl, cascade)))

      response.copy(
        body = response.body,
        attributes = response.attributes.insert(PushSupport.pushLocationKey, newPushResouces))
    }

  }

  private def locToRequest[F[_]: Functor](push: PushLocation, req: Request[F]): Request[F] =
    req.withPathInfo(push.location)

  private def collectResponse[F[_]](
      r: Vector[PushLocation],
      req: Request[F],
      verify: String => Boolean,
      routes: HttpRoutes[F])(implicit F: Monad[F]): F[Vector[PushResponse[F]]] =
    r.foldLeft(F.pure(Vector.empty[PushResponse[F]])) { (facc, v) =>
      if (verify(v.location)) {
        val newReq = locToRequest(v, req)
        if (v.cascade) facc.flatMap { accumulated => // Need to gather the sub resources
          routes
            .mapF[OptionT[F, ?], Vector[PushResponse[F]]] {
              _.semiflatMap { response =>
                response.attributes
                  .lookup(pushLocationKey)
                  .map { pushed =>
                    collectResponse(pushed, req, verify, routes).map(
                      accumulated ++ _ :+ PushResponse(v.location, response))
                  }
                  .getOrElse(F.pure(accumulated :+ PushResponse(v.location, response)))
              }
            }
            .apply(newReq)
            .getOrElse(Vector.empty[PushResponse[F]])
        } else {
          routes
            .flatMapF { response =>
              OptionT.liftF(facc.map(_ :+ PushResponse(v.location, response)))
            }
            .apply(newReq)
            .getOrElse(Vector.empty[PushResponse[F]])
        }
      } else facc
    }

  /** Transform the route such that requests will gather pushed resources
    *
    * @param routes HttpRoutes to transform
    * @param verify method that determines if the location should be pushed
    * @return      Transformed route
    */
  def apply[F[_]: Monad](
      @deprecatedName('service) routes: HttpRoutes[F],
      verify: String => Boolean = _ => true): HttpRoutes[F] = {

    def gather(req: Request[F])(resp: Response[F]): Response[F] =
      resp.attributes
        .lookup(pushLocationKey)
        .map { fresource =>
          val collected = collectResponse(fresource, req, verify, routes)
          resp.copy(
            body = resp.body,
            attributes = resp.attributes.insert(pushResponsesKey[F], collected)
          )
        }
        .getOrElse(resp)

    Kleisli(req => routes(req).map(gather(req)))
  }

  private[PushSupport] final case class PushLocation(location: String, cascade: Boolean)
  private[http4s] final case class PushResponse[F[_]](location: String, resp: Response[F])

  private[PushSupport] val pushLocationKey = Key.newKey[IO, Vector[PushLocation]].unsafeRunSync
  private[http4s] def pushResponsesKey[F[_]]: Key[F[Vector[PushResponse[F]]]] =
    Keys.PushResponses.asInstanceOf[Key[F[Vector[PushResponse[F]]]]]

  private[this] object Keys {
    val PushResponses: Key[Any] = Key.newKey[IO, Any].unsafeRunSync
  }
}
