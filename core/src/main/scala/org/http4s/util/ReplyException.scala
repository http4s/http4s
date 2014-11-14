package org.http4s.util

import org.http4s.{HttpVersion, Response}

import scalaz.concurrent.Task


trait ReplyException { self: Exception =>
  def asResponse(version: HttpVersion): Response

  def asTask: Task[Nothing] = Task.fail(this)
}
