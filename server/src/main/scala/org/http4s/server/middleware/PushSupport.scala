package org.http4s
package server
package middleware

import cats.{Functor, Monad}
import cats.data.Kleisli
import cats.effect.IO
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

  private def collectResponse[F[_]](
      r: Vector[PushLocation],
      req: Request[F],
      verify: String => Boolean,
      routes: HttpRoutes[F])(implicit F: Monad[F]): F[Vector[PushResponse[F]]] = {
    val emptyCollect: F[Vector[PushResponse[F]]] = F.pure(Vector.empty[PushResponse[F]])

    def fetchAndAdd(facc: F[Vector[PushResponse[F]]], v: PushLocation): F[Vector[PushResponse[F]]] =
      routes(req.withPathInfo(v.location)).value.flatMap {
        case None => emptyCollect
        case Some(response) if !v.cascade =>
          facc.map(_ :+ PushResponse(v.location, response))
        case Some(response) if v.cascade =>
          val pr = PushResponse(v.location, response)
          response.attributes.lookup(pushLocationKey) match {
            case Some(pushed) => // Need to gather the sub resources
              val fsubs = collectResponse(pushed, req, verify, routes)
              F.map2(facc, fsubs)(_ ++ _ :+ pr)
            case None => facc.map(_ :+ pr)
          }
      }

    r.filter(x => verify(x.location)).foldLeft(emptyCollect)(fetchAndAdd)
  }

  /** Transform the route such that requests will gather pushed resources
    *
    * @param routes HttpRoutes to transform
    * @param verify method that determines if the location should be pushed
    * @return      Transformed route
    */
  def apply[F[_]: Monad](
      routes: HttpRoutes[F],
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
