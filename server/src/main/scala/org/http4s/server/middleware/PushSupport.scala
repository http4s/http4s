package org.http4s
package server
package middleware

import fs2.Task
import org.http4s.batteries._
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

  private def handleException(t: Throwable): Unit = {
    logger.error(t)("Push resource route failure")
  }

  private def locToRequest(push: PushLocation, req: Request): Request =
    req.withPathInfo(push.location)

  private def collectResponse(r: Vector[PushLocation], req: Request, verify: String => Boolean, route: HttpService): Task[Vector[PushResponse]] =
    r.foldLeft(Task.now(Vector.empty[PushResponse])){ (facc, v) =>
      if (verify(v.location)) {
        val newReq = locToRequest(v, req)
        if (v.cascade) facc.flatMap { accumulated => // Need to gather the sub resources
          try route.flatMapF {
            case response: Response =>
              response.attributes.get(pushLocationKey).map { pushed =>
                collectResponse(pushed, req, verify, route)
                  .map(accumulated ++ _ :+ PushResponse(v.location, response))
              }.getOrElse(Task.now(accumulated:+PushResponse(v.location, response)))
            case Pass => Task.now(Vector.empty)
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

    Service.lift { req => service(req).map(_.cata(gather(req, _), Pass)) }
  }

  private [PushSupport] final case class PushLocation(location: String, cascade: Boolean)
  private [http4s] final case class PushResponse(location: String, resp: Response)

  private[PushSupport] val pushLocationKey = AttributeKey.http4s[Vector[PushLocation]]("pushLocation")
  private[http4s] val pushResponsesKey = AttributeKey.http4s[Task[Vector[PushResponse]]]("pushResponses")
}

