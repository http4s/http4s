package org.http4s

import org.http4s.Status._

import scalaz.concurrent.Task




/**
 * @author Bryce Anderson
 *         Created on 12/23/13
 */
object MockRoute {

  def route(): HttpService = {
    case req: Request if req.requestUri.pathString ==  "/ping" =>
      Ok("pong")

    case req: Request if req.requestMethod == Method.Post && req.requestUri.pathString == "/echo" =>
      Task.now(Response(body = req.body))

    case req: Request if req.requestUri.pathString == "/fail" =>
      sys.error("Problem!")
      Ok("No problem...")

    /** For testing the UrlTranslation middleware */
    case req: Request if req.requestUri.pathString == "/checktranslate" =>
      import org.http4s.util.middleware.URITranslation._
      val newpath = req.attributes.get(translateRootKey)
        .map(f => f("foo"))
        .getOrElse("bar!")

      Ok(newpath)

    /** For testing the PushSupport middleware */
    case req: Request if req.requestUri.pathString == "/push" =>
      import org.http4s.util.middleware.PushSupport._
      Ok("Hello").push("/ping")(req)
  }
}
