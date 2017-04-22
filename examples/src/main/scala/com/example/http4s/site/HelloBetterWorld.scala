package com.example.http4s
package site

import cats._
import cats.implicits._
import fs2.interop.cats._
import fs2.Task
import org.http4s._
import org.http4s.dsl._

object HelloBetterWorld {
  val service = HttpService[Task] {
    //  We use http4s-dsl to match the path of the Request to the familiar URI form
    case GET -> Root / "hello" =>
      // We could make a Task[Response] manually, but we use the
      // EntityResponseGenerator 'Ok' for convenience
      Ok("Hello, better world.")
  }
}
