package org.http4s

import scalaz.concurrent.Task

package object servlet {
  protected[servlet] type BodyWriter = Response => Task[Unit]

  protected[servlet] val NullBodyWriter: BodyWriter = { _ => Task.now(()) }

  protected[servlet] val DefaultChunkSize = 4096
}
