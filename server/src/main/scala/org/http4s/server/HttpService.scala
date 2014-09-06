package org.http4s
package server

import scalaz.concurrent.Task

object HttpService {
  val empty: HttpService = _ => Task.fail(Pass)
}
