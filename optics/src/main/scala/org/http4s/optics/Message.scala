package org.http4s.optics

import monocle.Prism
import monocle.macros.GenPrism
import org.http4s.{Message, Response, Request}

object message {
  val request: Prism[Message, Request] = GenPrism[Message, Request]
  val response: Prism[Message, Response] = GenPrism[Message, Response]
}