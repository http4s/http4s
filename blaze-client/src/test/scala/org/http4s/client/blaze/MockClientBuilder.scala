package org.http4s.client.blaze

import java.nio.ByteBuffer

import org.http4s.blaze.pipeline.{LeafBuilder, HeadStage}

import scalaz.concurrent.Task

object MockClientBuilder {
  def builder(head: => HeadStage[ByteBuffer], tail: => BlazeClientStage): ConnectionBuilder = {
    req => Task.delay {
      val t = tail
      LeafBuilder(t).base(head)
      t
    }
  }

  def manager(head: => HeadStage[ByteBuffer], tail: => BlazeClientStage): ConnectionManager = {
    ConnectionManager.basic(builder(head, tail))
  }
}
