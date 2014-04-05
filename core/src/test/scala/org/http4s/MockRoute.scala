package org.http4s

import scalaz.concurrent.Task

/**
 * @author Bryce Anderson
 *         Created on 12/23/13
 */
object MockRoute extends Http4s {

  def route(): HttpService = {
    case req: Request if req.requestUri.path ==  "/ping" =>
      Ok("pong")

    case req: Request if req.requestMethod == Method.Post && req.requestUri.path == "/echo" =>
      Task.now(Response(body = req.body))

    case req: Request if req.requestUri.path == "/fail" =>
      sys.error("Problem!")
      Ok("No problem...")

    /** For testing the UrlTranslation middleware */
    case req: Request if req.requestUri.path == "/checktranslate" =>
      import org.http4s.middleware.URITranslation._
      val newpath = req.attributes.get(translateRootKey)
        .map(f => f("foo"))
        .getOrElse("bar!")

      Ok(newpath)

    /** For testing the PushSupport middleware */
    case req: Request if req.requestUri.path == "/push" =>
      import org.http4s.middleware.PushSupport._
      Ok("Hello").push("/ping")(req)
  }
}
