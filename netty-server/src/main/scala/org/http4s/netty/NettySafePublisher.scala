package org.http4s.netty

import fs2.Chunk
import io.netty.handler.codec.http.HttpContent
import org.reactivestreams.{Publisher, Subscriber, Subscription}

private[http4s] final class NettySafePublisher(pub: Publisher[HttpContent])
    extends Publisher[Chunk[Byte]] {
  import NettySafePublisher._
  def subscribe(s: Subscriber[_ >: Chunk[Byte]]): Unit =
    pub.subscribe(new ChunkEmitSubscriber(s))
}

object NettySafePublisher {
  private[http4s] class ChunkEmitSubscriber(subscriber: Subscriber[_ >: Chunk[Byte]])
      extends Subscriber[HttpContent] {
    def onError(t: Throwable): Unit = subscriber.onError(t)

    def onComplete(): Unit = subscriber.onComplete()

    def onNext(t: HttpContent): Unit = {
      val bytes = new Array[Byte](t.content().readableBytes())
      t.content().readBytes(bytes)
      t.release()
      subscriber.onNext(Chunk.Bytes(bytes))
    }

    def onSubscribe(s: Subscription): Unit = subscriber.onSubscribe(s)
  }

}
