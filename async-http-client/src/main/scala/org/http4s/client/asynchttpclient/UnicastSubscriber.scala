/*
 * Adapted from https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.0/examples/src/main/java/org/reactivestreams/example/unicast/SyncSubscriber.java
 */
package org.http4s.client.asynchttpclient

import org.reactivestreams.{Subscription, Subscriber}
import org.log4s.getLogger

abstract class UnicastSubscriber[A](bufferSize: Int = 8) extends Subscriber[A] {
  private[this] val logger = getLogger

  private[this] var subscription: Subscription = _ // Obeying rule 3.1, we make this private!
  private[this] var done: Boolean = false

  override def onSubscribe(s: Subscription): Unit = {
    // Rule 2.13
    if (s == null) throw null

    if (subscription != null) { // If someone has made a mistake and added this Subscriber multiple times, let's handle it gracefully
      logger.error(s"This is a unicast subscriber. Canceling second subscription $s")
      try s.cancel() // Cancel the additional subscription
      catch {
        case t: Throwable =>
          // Subscription.cancel is not allowed to throw an exception, according to rule 3.15
          logger.error(t)(s"$s violated the Reactive Streams rule 3.15 by throwing an exception from cancel.")
      }
    }
    else {
      logger.info(s"Subscriber $this starting subscription to $s")
      // We have to assign it locally before we use it, if we want to be a synchronous `Subscriber`
      // Because according to rule 3.10, the Subscription is allowed to call `onNext` synchronously from within `request`
      subscription = s
      request(1)
    }
  }

  override def onNext(element: A): Unit = {
    logger.debug(s"Received element $element")
    if (subscription == null) { // Technically this check is not needed, since we are expecting Publishers to conform to the spec
      logger.error(new IllegalStateException)("Publisher violated the Reactive Streams rule 1.09 signalling onNext prior to onSubscribe.")
    } else {
      // As per rule 2.13, we need to throw a `java.lang.NullPointerException` if the `element` is `null`
      if (element == null) throw null
      if (!done) { // If we aren't already done
        try {
          if (!whenNext(element))
            finish()
        }
        catch {
          case t: Throwable =>
            finish()
            try onError(t)
            catch {
              case t2: Throwable =>
                // Subscriber.onError is not allowed to throw an exception, according to rule 2.13
                logger.error(t2)(s"$this violated the Reactive Streams rule 2.13 by throwing an exception from onError.")
            }
        }
      }
      else {
        logger.info(s"We were already done before we received $element. Not handed to whenNext.")
      }
    }
  }

  // Showcases a convenience method to idempotently marking the Subscriber as "done", so we don't want to process more elements
  // here for we also need to cancel our `Subscription`.
  private def finish(): Unit = {
    logger.info(s"Subscriber $this closing subscription to $subscription")
    //On this line we could add a guard against `!done`, but since rule 3.7 says that `Subscription.cancel()` is idempotent, we don't need to.
    done = true // If we `whenNext` throws an exception, let's consider ourselves done (not accepting more elements)
    try subscription.cancel() // Cancel the subscription
    catch {
      case t: Throwable =>
        //Subscription.cancel is not allowed to throw an exception, according to rule 3.15
        logger.error(t)(s"$subscription violated the Reactive Streams rule 3.15 by throwing an exception from cancel.")
    }
  }

  // This method is left as an exercise to the reader/extension point
  // Returns whether more elements are desired or not, and if no more elements are desired
  protected def whenNext(element: A): Boolean

  protected def request(n: Int): Unit = {
    try {
      logger.debug(s"Triggering request of $n elements to $subscription")
      // If we want elements, according to rule 2.1 we need to call `request`
      // And, according to rule 3.2 we are allowed to call this synchronously from within the `onSubscribe` method
      subscription.request(n)
    }
    catch {
      case t: Throwable =>
        // Subscription.request is not allowed to throw according to rule 3.16
        logger.error(t)(s"$subscription violated the Reactive Streams rule 3.16 by throwing an exception from request.")
    }
  }

  override def onError(t: Throwable): Unit = {
    if (subscription == null) { // Technically this check is not needed, since we are expecting Publishers to conform to the spec
      logger.error("Publisher violated the Reactive Streams rule 1.09 signalling onError prior to onSubscribe.")
    }
    else {
      // As per rule 2.13, we need to throw a `java.lang.NullPointerException` if the `Throwable` is `null`
      if (t == null) throw null
      // Here we are not allowed to call any methods on the `Subscription` or the `Publisher`, as per rule 2.3
      // And anyway, the `Subscription` is considered to be cancelled if this method gets called, as per rule 2.4
    }
  }

  override def onComplete(): Unit = {
    if (subscription == null) { // Technically this check is not needed, since we are expecting Publishers to conform to the spec
      logger.error("Publisher violated the Reactive Streams rule 1.09 signalling onComplete prior to onSubscribe.")
    } else {
      // Here we are not allowed to call any methods on the `Subscription` or the `Publisher`, as per rule 2.3
      // And anyway, the `Subscription` is considered to be cancelled if this method gets called, as per rule 2.4
    }
  }
}
