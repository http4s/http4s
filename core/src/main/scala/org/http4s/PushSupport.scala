package org.http4s

import scala.concurrent.Future
import play.api.libs.iteratee.Iteratee

/**
 * @author Bryce Anderson
 *         Created on 11/5/13
 */

object PushSupport {

  // TODO: choose the right ec
  import concurrent.ExecutionContext.Implicits.global

  // An implicit conversion class
  implicit class PushSupportResponder(responder: Responder) extends AnyRef {
    def push(url: String, cascade: Boolean = true): Responder = {
      val newPushResouces = responder.attributes.get(pushLocationKey)
          .map(_ :+ PushLocation(url, cascade))
          .getOrElse(Vector(PushLocation(url,cascade)))

      Responder(responder.prelude, responder.body,
        responder.attributes.put(PushSupport.pushLocationKey, newPushResouces))
    }
  }

  private def locToRequest(push: PushLocation, prelude: RequestPrelude): RequestPrelude =
    RequestPrelude(pathInfo = push.location, headers = prelude.headers)

  private def collectResponder(r: Vector[PushLocation], req: RequestPrelude, route: Route): Future[Vector[PushResponder]] = 
    r.foldLeft(Future.successful(Vector.empty[PushResponder])){ (facc, v) =>
      if (v.cascade) facc.flatMap { accumulated => // Need to gather the sub resources
        route(locToRequest(v, req)).run
          .flatMap { responder =>             // Inside the future result of this pushed resource
            responder.attributes.get(pushLocationKey)
            .map { pushed =>
              collectResponder(pushed, req, route)
                .map(accumulated ++ _ :+ PushResponder(v.location, responder))
            }.getOrElse(Future.successful(accumulated:+PushResponder(v.location, responder)))
          }
      } else {
        route(locToRequest(v, req))
          .run
          .flatMap( resp => facc.map(_ :+ PushResponder(v.location, resp)))
      }
    }
  

  /** Transform the route such that requests will gather pushed resources
   *
   * @param route Route to transform
   * @return      Transformed route
   */
  def apply(route: Route): Route = {
    def gather(req: RequestPrelude, i: Iteratee[Chunk, Responder]): Iteratee[Chunk, Responder] = i map { resp =>
      resp.attributes.get(pushLocationKey).map { fresource =>
        val collected: Future[Vector[PushResponder]] = collectResponder(fresource, req, route)
        Responder(resp.prelude, resp.body, resp.attributes.put(pushRespondersKey, collected))
      }.getOrElse(resp)
    }

    new Route {
      def apply(v1: RequestPrelude): Iteratee[Chunk, Responder] = gather(v1, route(v1))
      def isDefinedAt(x: RequestPrelude): Boolean = route.isDefinedAt(x)
    }
  }

  private [PushSupport] case class PushLocation(location: String, cascade: Boolean)
  private [PushSupport] case class PushResponder(location: String, resp: Responder)

  private[PushSupport] val pushLocationKey = AttributeKey[Vector[PushLocation]]("http4sPush")
  private[http4s] val pushRespondersKey = AttributeKey[Future[Vector[PushResponder]]]("http4sPushResponders")
}