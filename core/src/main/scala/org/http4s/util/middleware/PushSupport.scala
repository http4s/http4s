package org.http4s.util.middleware

import com.typesafe.scalalogging.slf4j.Logging
import scalaz.concurrent.Task
import org.http4s._
import org.http4s.Request
import org.http4s.AttributeKey

/**
 * @author Bryce Anderson
 *         Created on 11/5/13
 */

object PushSupport extends Logging {

  // TODO: choose the right ec
  import concurrent.ExecutionContext.Implicits.global

  // An implicit conversion class
  implicit class PushSupportResponse(response: Task[Response]) extends AnyRef {
    def push(url: String, cascade: Boolean = true): Task[Response] = response.map { response =>
      val newPushResouces = response.attributes.get(pushLocationKey)
          .map(_ :+ PushLocation(url, cascade))
          .getOrElse(Vector(PushLocation(url,cascade)))

      response.copy(
        body = response.body,
        attributes = response.attributes.put(PushSupport.pushLocationKey, newPushResouces))
    }
  }

  private def handleException(ex: Throwable) {
    logger.error("Push resource route failure", ex)
  }

  private def locToRequest(push: PushLocation, req: Request): Request =
    Request(pathInfo = push.location, headers = req.headers)

  private def collectResponse(r: Vector[PushLocation], req: Request, route: HttpService): Task[Vector[PushResponse]] =
    r.foldLeft(Task.now(Vector.empty[PushResponse])){ (facc, v) =>
      val newReq = locToRequest(v, req)
      if (v.cascade) facc.flatMap { accumulated => // Need to gather the sub resources
        try route(newReq)
          .flatMap { response =>                  // Inside the future result of this pushed resource
            response.attributes.get(pushLocationKey)
            .map { pushed =>
              collectResponse(pushed, req, route)
                .map(accumulated ++ _ :+ PushResponse(v.location, response))
            }.getOrElse(Task.now(accumulated:+PushResponse(v.location, response)))
          }
        catch { case t: Throwable => handleException(t); facc }
      } else {
        try route(newReq)   // Need to make sure to catch exceptions
          .flatMap( resp => facc.map(_ :+ PushResponse(v.location, resp)))
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
        val collected: Task[Vector[PushResponse]] = collectResponse(fresource, req, route)
        resp.copy(
          body = resp.body,
          attributes = resp.attributes.put(pushResponsesKey, collected))
      }.getOrElse(resp)
    }

    new HttpService {
      def apply(v1: Request): Task[Response] = gather(v1, route(v1))
    }
  }

  private [PushSupport] case class PushLocation(location: String, cascade: Boolean)
  private [http4s] case class PushResponse(location: String, resp: Response)

  private[PushSupport] val pushLocationKey = AttributeKey.http4s[Vector[PushLocation]]("pushLocation")
  private[http4s] val pushResponsesKey = AttributeKey.http4s[Task[Vector[PushResponse]]]("pushResponses")
}