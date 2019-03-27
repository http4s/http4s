package org.http4s
package client
package blaze

import cats.effect.IO
import java.nio.ByteBuffer
import org.http4s.blaze.pipeline.{HeadStage, LeafBuilder}

private[blaze] object MockClientBuilder {
  def builder(
      head: => HeadStage[ByteBuffer],
      tail: => BlazeConnection[IO]): ConnectionBuilder[IO, BlazeConnection[IO]] = { _ =>
    IO {
      val t = tail
      LeafBuilder(t).base(head)
      t
    }
  }

  def manager(
      head: => HeadStage[ByteBuffer],
      tail: => BlazeConnection[IO]): ConnectionManager[IO, BlazeConnection[IO]] =
    ConnectionManager.basic(builder(head, tail))
}
