package org.lyranthe.grpc.java_runtime.client

import io.grpc._

import scala.collection.mutable.ArrayBuffer

class DummyClientCall extends ClientCall[String, Int] {
  var requested: Int = 0
  val messagesSent: ArrayBuffer[String] = ArrayBuffer[String]()
  var listener: Option[ClientCall.Listener[Int]] = None

  override def start(responseListener: ClientCall.Listener[Int],
                     headers: Metadata): Unit =
    listener = Some(responseListener)

  override def request(numMessages: Int): Unit = requested += numMessages

  override def cancel(message: String, cause: Throwable): Unit = ()

  override def halfClose(): Unit = ()

  override def sendMessage(message: String): Unit = messagesSent += message
}
