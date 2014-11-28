package org.http4s

import scalaz.concurrent.Task

/** Mixin for Exceptions that can be interpreted as a Response */
trait ReplyException { self: Exception =>

  /** Make a [[Response]] representation of this Exception */
  def asResponse(version: HttpVersion): Response

  /** Make a failed `Task[Nothing]` representation of this Exception */
  def asTask: Task[Nothing] = Task.fail(this)
}
