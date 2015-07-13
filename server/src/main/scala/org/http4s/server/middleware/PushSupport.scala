package org.http4s
package server
package middleware

import scalaz.concurrent.Task
import scalaz.Kleisli.kleisli
import org.log4s.getLogger

object PushSupport {
  private[this] val logger = getLogger

  implicit class PushOps(response: Task[Response]) {
    def push(url: String, cascade: Boolean = true)(implicit req: Request): Task[Response] = response.map { response =>
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

  private def handleException(t: Throwable) {
    logger.error(t)("Push resource route failure")
  }

  private def locToRequest(push: PushLocation, req: Request): Request =
    req.withPathInfo(push.location)

  private def collectResponse(r: Vector[PushLocation], req: Request, verify: String => Boolean, route: HttpService): Task[Vector[PushResponse]] =
    r.foldLeft(Task.now(Vector.empty[PushResponse])){ (facc, v) =>
      if (verify(v.location)) {
        val newReq = locToRequest(v, req)
        if (v.cascade) facc.flatMap { accumulated => // Need to gather the sub resources
          try route.flatMapTask { response =>
            response.attributes.get(pushLocationKey).map { pushed =>
              collectResponse(pushed, req, verify, route)
                .map(accumulated ++ _ :+ PushResponse(v.location, response))
            }.getOrElse(Task.now(accumulated:+PushResponse(v.location, response)))
          }.apply(newReq)
          catch { case t: Throwable => handleException(t); facc }
        } else {
          try route.flatMapTask { resp => // Need to make sure to catch exceptions
            facc.map(_ :+ PushResponse(v.location, resp))
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
  def apply(service: HttpService, verify: String => Boolean = _ => true): HttpService = {

    def gather(req: Request, resp: Response): Response = {
      resp.attributes.get(pushLocationKey).map { fresource =>
        val collected: Task[Vector[PushResponse]] = collectResponse(fresource, req, verify, service)
        resp.copy(
          body = resp.body,
          attributes = resp.attributes.put(pushResponsesKey, collected)
        )
      }.getOrElse(resp)
    }

    Service { req => service(req).map(gather(req, _)) }
  }

  private [PushSupport] case class PushLocation(location: String, cascade: Boolean)
  private [http4s] case class PushResponse(location: String, resp: Response)

  private[PushSupport] val pushLocationKey = AttributeKey.http4s[Vector[PushLocation]]("pushLocation")
  private[http4s] val pushResponsesKey = AttributeKey.http4s[Task[Vector[PushResponse]]]("pushResponses")
}

