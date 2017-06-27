package org.http4s
package server
package middleware

import cats._
import cats.implicits._
import org.log4s.getLogger

object PushSupport {
  private[this] val logger = getLogger

  implicit class PushOps[F[_]: Functor](response: F[Response[F]]) {
    def push(url: String, cascade: Boolean = true)(implicit req: Request[F]): F[Response[F]] = response.map { response =>
      val newUrl = {
        val script = req.scriptName
        if (script.length > 0) {
          val sb = new StringBuilder()
          sb.append(script)
          if (!url.startsWith("/")) sb.append('/')
          sb.append(url)
            .result()
        }
        else url
      }

      logger.trace(s"Adding push resource: $newUrl")

      val newPushResouces = response.attributes.get(pushLocationKey)
        .map(_ :+ PushLocation(newUrl, cascade))
        .getOrElse(Vector(PushLocation(newUrl,cascade)))

      response.copy(
        body = response.body,
        attributes = response.attributes.put(PushSupport.pushLocationKey, newPushResouces))
    }
  }

  private def handleException(t: Throwable): Unit = {
    logger.error(t)("Push resource route failure")
  }

  private def locToRequest[F[_]: Functor](push: PushLocation, req: Request[F]): Request[F] =
    req.withPathInfo(push.location)

  private def collectResponse[F[_]](r: Vector[PushLocation],
                                    req: Request[F],
                                    verify: String => Boolean, route: HttpService[F])
                                   (implicit F: Monad[F]): F[Vector[PushResponse[F]]] =
    r.foldLeft(F.pure(Vector.empty[PushResponse[F]])){ (facc, v) =>
      if (verify(v.location)) {
        val newReq = locToRequest(v, req)
        if (v.cascade) facc.flatMap { accumulated => // Need to gather the sub resources
          try route.flatMapF {
            case response: Response[F] =>
              response.attributes.get(pushLocationKey).map { pushed =>
                collectResponse(pushed, req, verify, route)
                  .map(accumulated ++ _ :+ PushResponse(v.location, response))
              }.getOrElse(F.pure(accumulated :+ PushResponse(v.location, response)))
            case Pass() =>
              F.pure(Vector.empty[PushResponse[F]])
          }.apply(newReq)
          catch { case t: Throwable => handleException(t); facc }
        } else {
          try route.flatMapF { resp => // Need to make sure to catch exceptions
            facc.map(_ :+ PushResponse(v.location, resp.orNotFound))
          }.apply(newReq)
          catch { case t: Throwable => handleException(t); facc }
        }
      }
      else facc
    }
  

  /** Transform the route such that requests will gather pushed resources
   *
   * @param service HttpService to transform
   * @param verify method that determines if the location should be pushed
   * @return      Transformed route
   */
  def apply[F[_]: Monad](service: HttpService[F], verify: String => Boolean = _ => true): HttpService[F] = {

    def gather(req: Request[F], resp: Response[F]): Response[F] = {
      resp.attributes.get(pushLocationKey).map { fresource =>
        val collected: F[Vector[PushResponse[F]]] = collectResponse(fresource, req, verify, service)
        resp.copy(
          body = resp.body,
          attributes = resp.attributes.put(pushResponsesKey[F], collected)
        )
      }.getOrElse(resp)
    }

    Service.lift { req => service(req).map(_.cata(gather(req, _), Pass())) }
  }

  private [PushSupport] final case class PushLocation(location: String, cascade: Boolean)
  private [http4s] final case class PushResponse[F[_]](location: String, resp: Response[F])

  private[PushSupport] val pushLocationKey = AttributeKey.http4s[Vector[PushLocation]]("pushLocation")
  private[http4s] def pushResponsesKey[F[_]] = AttributeKey.http4s[F[Vector[PushResponse[F]]]]("pushResponses")
}

