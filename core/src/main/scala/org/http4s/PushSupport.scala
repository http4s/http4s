package org.http4s

import scala.concurrent.Future
import play.api.libs.iteratee.Iteratee
import com.typesafe.scalalogging.slf4j.Logging

/**
 * @author Bryce Anderson
 *         Created on 11/5/13
 */

object PushSupport extends Logging {

  // TODO: choose the right ec
  import concurrent.ExecutionContext.Implicits.global

  // An implicit conversion class
  implicit class PushSupportResponder(response: Response) extends AnyRef {
    def push(url: String, cascade: Boolean = true): Response = {
      val newPushResouces = response.attributes.get(pushLocationKey)
          .map(_ :+ PushLocation(url, cascade))
          .getOrElse(Vector(PushLocation(url,cascade)))

      Response(response.prelude, response.body,
        response.attributes.put(PushSupport.pushLocationKey, newPushResouces))
    }
  }

  private def handleException(ex: Throwable) {
    logger.error("Push resource route failure", ex)
  }

  private def locToRequest(push: PushLocation, prelude: RequestPrelude): RequestPrelude =
    RequestPrelude(pathInfo = push.location, headers = prelude.headers)

  private def collectResponder(r: Vector[PushLocation], req: RequestPrelude, route: Route): Future[Vector[PushResponse]] = 
    r.foldLeft(Future.successful(Vector.empty[PushResponse])){ (facc, v) =>
      val newReq = locToRequest(v, req)
      if (v.cascade) facc.flatMap { accumulated => // Need to gather the sub resources
        try route(newReq).run
          .flatMap { response =>                  // Inside the future result of this pushed resource
            response.attributes.get(pushLocationKey)
            .map { pushed =>
              collectResponder(pushed, req, route)
                .map(accumulated ++ _ :+ PushResponse(v.location, response))
            }.getOrElse(Future.successful(accumulated:+PushResponse(v.location, response)))
          }
        catch { case t: Throwable => handleException(t); facc }
      } else {
        try route(newReq)   // Need to make sure to catch exceptions
          .run
          .flatMap( resp => facc.map(_ :+ PushResponse(v.location, resp)))
        catch { case t: Throwable => handleException(t); facc }
      }
    }
  

  /** Transform the route such that requests will gather pushed resources
   *
   * @param route Route to transform
   * @return      Transformed route
   */
  def apply(route: Route): Route = {
    def gather(req: RequestPrelude, i: Iteratee[Chunk, Response]): Iteratee[Chunk, Response] = i map { resp =>
      resp.attributes.get(pushLocationKey).map { fresource =>
        val collected: Future[Vector[PushResponse]] = collectResponder(fresource, req, route)
        Response(resp.prelude, resp.body, resp.attributes.put(pushResponseKey, collected))
      }.getOrElse(resp)
    }

    new Route {
      def apply(v1: RequestPrelude): Iteratee[Chunk, Response] = gather(v1, route(v1))
      def isDefinedAt(x: RequestPrelude): Boolean = route.isDefinedAt(x)
    }
  }

  private [PushSupport] case class PushLocation(location: String, cascade: Boolean)
  private [PushSupport] case class PushResponse(location: String, resp: Response)

  private[PushSupport] val pushLocationKey = AttributeKey[Vector[PushLocation]]("http4sPush")
  private[http4s] val pushResponseKey = AttributeKey[Future[Vector[PushResponse]]]("http4sPushResponders")
}