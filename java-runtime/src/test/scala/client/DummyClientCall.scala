package org.lyranthe.fs2_grpc
package java_runtime
package client

import scala.collection.mutable.ArrayBuffer
import io.grpc._

class DummyClientCall extends ClientCall[String, Int] {
  var requested: Int                             = 0
  val messagesSent: ArrayBuffer[String]          = ArrayBuffer[String]()
  var listener: Option[ClientCall.Listener[Int]] = None
  var cancelled: Option[(String, Throwable)]     = None

  override def start(responseListener: ClientCall.Listener[Int], headers: Metadata): Unit =
    listener = Some(responseListener)

  override def request(numMessages: Int): Unit = requested += numMessages

  override def cancel(message: String, cause: Throwable): Unit =
    cancelled = Some((message, cause))

  override def halfClose(): Unit = ()

  override def sendMessage(message: String): Unit = {
    messagesSent += message
    ()
  }
}
