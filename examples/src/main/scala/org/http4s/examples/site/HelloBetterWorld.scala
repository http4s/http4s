package org.http4s.examples.site

import org.http4s.Http4s._
import org.http4s.dsl._
import org.http4s.server.HttpService

object HelloBetterWorld {
  /// code_ref: service
  val service: HttpService = {
    //  We use http4s-dsl to match the path of the Request to the familiar URI form
    case GET -> Root / "hello" =>
      // We could make a Task[Response] manually, but we use the
      // EntityResponseGenerator 'Ok' for convenience
      Ok("Hello, better world.")
  }
  /// end_code_ref
}