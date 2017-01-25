package org.http4s

import fs2.Task

package object servlet {
  protected[servlet] type BodyWriter = Response => Task[Unit]

  protected[servlet] val NullBodyWriter: BodyWriter = { _ => Task.now(()) }

  protected[servlet] val DefaultChunkSize = 4096
}
