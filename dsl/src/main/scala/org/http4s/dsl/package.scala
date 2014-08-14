package org.http4s

import scalaz.concurrent.Task

package object dsl extends Http4s with Http4sConstants {
  implicit final class MethodSyntax(val self: Method) extends AnyVal {
    /** Make a [[org.http4s.Request]] using this Method */
    def apply(uri: Uri): Task[Request] = Task.now(Request(self, uri))
  }
}

