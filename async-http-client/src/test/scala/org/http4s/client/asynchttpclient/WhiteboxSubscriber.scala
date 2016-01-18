package org.http4s.client.asynchttpclient

import org.reactivestreams.tck.SubscriberWhiteboxVerification.{SubscriberPuppet, WhiteboxSubscriberProbe}
import org.reactivestreams.{Subscription, Subscriber}

/** Stackable trait to ease creating whitebox subscriber tests. */
trait WhiteboxSubscriber[A] extends Subscriber[A] {
  def probe: WhiteboxSubscriberProbe[A]

  abstract override def onError(t: Throwable): Unit = {
    super.onError(t)
    probe.registerOnError(t)
  }

  abstract override def onSubscribe(s: Subscription): Unit = {
    super.onSubscribe(s)
    probe.registerOnSubscribe(new SubscriberPuppet {
      override def triggerRequest(elements: Long): Unit = {
        s.request(elements)
      }

      override def signalCancel(): Unit = {
        s.cancel()
      }
    })
  }

  abstract override def onComplete(): Unit = {
    super.onComplete()
    probe.registerOnComplete()
  }

  abstract override def onNext(a: A): Unit = {
    super.onNext(a)
    probe.registerOnNext(a)
  }
}
