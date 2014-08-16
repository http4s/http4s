package org.http4s.dsl.impl

import scalaz.concurrent.Task

import org.http4s.dsl.Path
import org.http4s.{Method, Request, Uri}

trait RequestGenerator extends Any {
  def method: Method

  /** Make a [[org.http4s.Request]] using this Method */
  def apply(uri: Uri): Task[Request] = Task.now(Request(method, uri))
}
