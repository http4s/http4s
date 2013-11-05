package org.http4s

import scala.concurrent.Future
import play.api.libs.iteratee.Iteratee

/**
 * @author Bryce Anderson
 *         Created on 11/5/13
 */

object PushSupport {

  import concurrent.ExecutionContext.Implicits.global

  // A pimping class
  implicit class PushSupportResponder(responder: Responder) extends AnyRef {
    def push(url: String, cascade: Boolean = true): Responder = {
      val newPushResouces = responder.attributes.get(pushLocationKey)
          .map(_.map(_ :+ PushLocation(url, cascade)))
          .getOrElse(Future.successful(Vector(PushLocation(url,cascade))))

      Responder(responder.prelude, responder.body,
        responder.attributes.put(PushSupport.pushLocationKey, newPushResouces))
    }
  }

  private def locToRequest(push: PushLocation): RequestPrelude = ???

  private def collectResponder(r: Future[Vector[PushLocation]], route: Route): Future[Vector[PushResponder]] = r.flatMap(
    _.foldLeft(Future.successful(Vector.empty[PushResponder])){ (f, v) =>
      if (v.cascasde) f.flatMap { vresp => // Need to gather the sub resources
        route(locToRequest(v)).run
          .flatMap { responder =>             // Inside the future result of this pushed resource
            responder.attributes.get(pushLocationKey)
            .map { fresp =>                   // More resources. Need to collect them and add all this up
               collectResponder(fresp, route).map(vresp ++ _ :+ PushResponder(v.location, responder))
            }.getOrElse(Future.successful(vresp:+PushResponder(v.location, responder)))
          }
      } else {
        route(locToRequest(v))
          .run
          .flatMap{ resp => f.map(_ :+ PushResponder(v.location, resp))}
      }
    }
  )

  /** Transform the route such that requests will gather pushed resources
   *
   * @param route Route to transform
   * @return      Transformed route
   */
  def apply(route: Route): Route = {
    def gather(i: Iteratee[Chunk, Responder]): Iteratee[Chunk, Responder] = i map { resp =>
      resp.attributes.get(pushLocationKey).map { fresource =>
        val collected: Future[Vector[PushResponder]] = collectResponder(fresource, route)
        Responder(resp.prelude, resp.body, resp.attributes.put(pushRespondersKey, collected))
      }.getOrElse(resp)
    }

    route andThen gather   // The backend will need to get the values in pushResponder and utilize them
  }

  private [PushSupport] case class PushLocation(location: String, cascasde: Boolean)
  private [PushSupport] case class PushResponder(location: String, resp: Responder)

  private[PushSupport] val pushLocationKey = AttributeKey[Future[Vector[PushLocation]]]("http4sPush")
  private[http4s] val pushRespondersKey = AttributeKey[Future[Vector[PushResponder]]]("http4sPushResponders")
}