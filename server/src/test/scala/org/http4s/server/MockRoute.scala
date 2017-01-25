package org.http4s
package server

import fs2._
import org.http4s.Status._
import org.http4s.server.middleware.PushSupport._

object MockRoute extends Http4s {

  def route(): HttpService = HttpService {
    case req: Request if req.uri.path ==  "/ping" =>
      Response(Ok).withBody("pong")

    case req: Request if req.method == Method.POST && req.uri.path == "/echo" =>
      Task.now(Response(body = req.body))

    case req: Request if req.uri.path ==  "/withslash" =>
      Task.now(Response(Ok))

    case req: Request if req.uri.path ==  "/withslash/" =>
      Task.now(Response(Accepted))

    case req: Request if req.uri.path == "/fail" =>
      sys.error("Problem!")

    /** For testing the PushSupport middleware */
    case req: Request if req.uri.path == "/push" =>
      Response(Ok).withBody("Hello").push("/ping")(req)
  }
}
