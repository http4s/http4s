package org.http4s

import scalaz.concurrent.Task

/** Mixin for Exceptions that can be interpreted as a Response */
trait ReplyException { self: Exception =>

  /** Make a [[Response]] representation of this Exception */
  def asResponse(version: HttpVersion): Task[Response]
}
