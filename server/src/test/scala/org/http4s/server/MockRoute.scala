package org.http4s
package server

import scalaz.concurrent.Task

object MockRoute extends Http4s {
  import middleware.PushSupport._

  def route(): HttpService = {
    case req: Request if req.requestUri.path ==  "/ping" =>
      ResponseBuilder(Ok, "pong")

    case req: Request if req.requestMethod == Method.Post && req.requestUri.path == "/echo" =>
      Task.now(Response(body = req.body))

    case req: Request if req.requestUri.path == "/fail" =>
      sys.error("Problem!")
      ResponseBuilder(Ok, "No problem...")

    /** For testing the UrlTranslation middleware */
    case req: Request if req.requestUri.path == "/checktranslate" =>
      import org.http4s.server.middleware.URITranslation._
      val newpath = req.attributes.get(translateRootKey)
        .map(f => f("foo"))
        .getOrElse("bar!")

      ResponseBuilder(Ok, newpath)

    /** For testing the PushSupport middleware */
    case req: Request if req.requestUri.path == "/push" =>
      ResponseBuilder(Ok, "Hello").push("/ping")(req)
  }
}
