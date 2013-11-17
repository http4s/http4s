package org.http4s

import com.typesafe.scalalogging.slf4j.Logging
import scalaz.concurrent.Task

/**
 * @author Bryce Anderson
 *         Created on 11/5/13
 */

object PushSupport extends Logging {

  // TODO: choose the right ec
  import concurrent.ExecutionContext.Implicits.global

  // An implicit conversion class
  implicit class PushSupportResponder(responder: Response) extends AnyRef {
    def push(url: String, cascade: Boolean = true): Response = {
      val newPushResouces = responder.attributes.get(pushLocationKey)
          .map(_ :+ PushLocation(url, cascade))
          .getOrElse(Vector(PushLocation(url,cascade)))

      Response(responder.prelude, responder.body,
        responder.attributes.put(PushSupport.pushLocationKey, newPushResouces))
    }
  }

  private def handleException(ex: Throwable) {
    logger.error("Push resource route failure", ex)
  }

  private def locToRequest(push: PushLocation, req: Request): Request =
    Request(RequestPrelude(pathInfo = push.location, headers = req.prelude.headers))

  private def collectResponder(r: Vector[PushLocation], req: Request, route: HttpService): Task[Vector[PushResponder]] =
    r.foldLeft(Task.delay(Vector.empty[PushResponder])){ (facc, v) =>
      val newReq = locToRequest(v, req)
      if (v.cascade) facc.flatMap { accumulated => // Need to gather the sub resources
        try route(newReq)
          .flatMap { response =>                  // Inside the future result of this pushed resource
            response.attributes.get(pushLocationKey)
            .map { pushed =>
              collectResponder(pushed, req, route)
                .map(accumulated ++ _ :+ PushResponder(v.location, response))
            }.getOrElse(Task.delay(accumulated:+PushResponder(v.location, response)))
          }
        catch { case t: Throwable => handleException(t); facc }
      } else {
        try route(newReq)   // Need to make sure to catch exceptions
          .flatMap( resp => facc.map(_ :+ PushResponder(v.location, resp)))
        catch { case t: Throwable => handleException(t); facc }
      }
    }
  

  /** Transform the route such that requests will gather pushed resources
   *
   * @param route Route to transform
   * @return      Transformed route
   */
  def apply(route: HttpService): HttpService = {
    def gather(req: Request, i: Task[Response]): Task[Response] = i map { resp =>
      resp.attributes.get(pushLocationKey).map { fresource =>
        val collected: Task[Vector[PushResponder]] = collectResponder(fresource, req, route)
        Response(resp.prelude, resp.body, resp.attributes.put(pushRespondersKey, collected))
      }.getOrElse(resp)
    }

    new HttpService {
      def apply(v1: Request): Task[Response] = gather(v1, route(v1))
//      def isDefinedAt(x: Request): Boolean = route.isDefinedAt(x)
    }
  }

  private [PushSupport] case class PushLocation(location: String, cascade: Boolean)
  private [PushSupport] case class PushResponder(location: String, resp: Response)

  private[PushSupport] val pushLocationKey = AttributeKey[Vector[PushLocation]]("http4sPush")
  private[http4s] val pushRespondersKey = AttributeKey[Task[Vector[PushResponder]]]("http4sPushResponders")
}