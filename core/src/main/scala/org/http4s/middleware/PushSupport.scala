package org.http4s
package middleware

import com.typesafe.scalalogging.slf4j.Logging
import scalaz.concurrent.Task
import URITranslation.translateRootKey
import scalaz.syntax.Ops

/**
 * @author Bryce Anderson
 *         Created on 11/5/13
 */

object PushSupport extends Logging {

  trait PushSyntax {
    implicit class PushOps(response: Task[Response]) {
      def push(url: String, cascade: Boolean = true)(implicit req: Request): Task[Response] = response.map { response =>
        val newUrl = req.attributes.get(translateRootKey)
          .map(f => f(url))
          .getOrElse(url)

        val newPushResouces = response.attributes.get(pushLocationKey)
          .map(_ :+ PushLocation(newUrl, cascade))
          .getOrElse(Vector(PushLocation(newUrl,cascade)))
        logger.trace(s"Adding push resource: $newUrl")
        response.copy(
          body = response.body,
          attributes = response.attributes.put(PushSupport.pushLocationKey, newPushResouces))
      }
    }
  }

  private def handleException(ex: Throwable) {
    logger.error("Push resource route failure", ex)
  }

  private def locToRequest(push: PushLocation, req: Request): Request =
    req.withPathInfo(push.location)

  private def collectResponse(r: Vector[PushLocation], req: Request, verify: String => Boolean, route: HttpService): Task[Vector[PushResponse]] =
    r.foldLeft(Task.now(Vector.empty[PushResponse])){ (facc, v) =>
      if (verify(v.location)) {
        val newReq = locToRequest(v, req)
        if (v.cascade) facc.flatMap { accumulated => // Need to gather the sub resources
          try route(newReq)
            .flatMap { response =>                  // Inside the future result of this pushed resource
              response.attributes.get(pushLocationKey)
              .map { pushed =>
                collectResponse(pushed, req, verify, route)
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

      else facc
    }
  

  /** Transform the route such that requests will gather pushed resources
   *
   * @param route Route to transform
   * @param verify method that determines if the location should be pushed
   * @return      Transformed route
   */
  def apply(route: HttpService, verify: String => Boolean = _ => true): HttpService = new HttpService {

    def apply(v1: Request): Task[Response] = gather(v1, route(v1))

    def isDefinedAt(x: Request): Boolean = route.isDefinedAt(x)

    private def gather(req: Request, i: Task[Response]): Task[Response] = i map { resp =>
      resp.attributes.get(pushLocationKey).map { fresource =>
        val collected: Task[Vector[PushResponse]] = collectResponse(fresource, req, verify, route)
        resp.copy(
          body = resp.body,
          attributes = resp.attributes.put(pushResponsesKey, collected))
      }.getOrElse(resp)
    }
  }

  private [PushSupport] case class PushLocation(location: String, cascade: Boolean)
  private [http4s] case class PushResponse(location: String, resp: Response)

  private[PushSupport] val pushLocationKey = AttributeKey.http4s[Vector[PushLocation]]("pushLocation")
  private[http4s] val pushResponsesKey = AttributeKey.http4s[Task[Vector[PushResponse]]]("pushResponses")
}

