package org.http4s
package client
package blaze

import java.nio.ByteBuffer

import org.http4s.blaze.pipeline.{LeafBuilder, HeadStage}

import fs2.Task

private object MockClientBuilder {
  def builder(head: => HeadStage[ByteBuffer], tail: => BlazeConnection): ConnectionBuilder[BlazeConnection] = {
    req => Task.delay {
      val t = tail
      LeafBuilder(t).base(head)
      t
    }
  }

  def manager(head: => HeadStage[ByteBuffer], tail: => BlazeConnection): ConnectionManager[BlazeConnection] = {
    ConnectionManager.basic(builder(head, tail))
  }
}
