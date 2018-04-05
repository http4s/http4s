package org.lyranthe.fs2_grpc.java_runtime
package client

import cats.effect.IO
import cats.effect.laws.util.TestContext
import fs2._
import io.grpc.{ManagedChannelBuilder, Metadata, Status, StatusRuntimeException}
import minitest._

import scala.util.Success

object ClientSuite extends SimpleTestSuite {
  test("single message to unaryToUnary") {
    implicit val ec = TestContext()

    val dummy  = new DummyClientCall()
    val client = new Fs2ClientCall[IO, String, Int](dummy)
    val result = client.unaryToUnaryCall("hello", new Metadata()).unsafeToFuture()
    dummy.listener.get.onMessage(5)

    // Check that call does not complete after result returns
    ec.tick()
    assertEquals(result.value, None)

    dummy.listener.get.onClose(Status.OK, new Metadata())

    // Check that call completes after status
    ec.tick()
    assertEquals(result.value, Some(Success(5)))
    assertEquals(dummy.messagesSent.size, 1)
    assertEquals(dummy.requested, 1)
  }

  test("no response message to unaryToUnary") {
    implicit val ec = TestContext()

    val dummy  = new DummyClientCall()
    val client = new Fs2ClientCall[IO, String, Int](dummy)
    val result = client.unaryToUnaryCall("hello", new Metadata()).unsafeToFuture()

    dummy.listener.get.onClose(Status.OK, new Metadata())

    // Check that call completes after status but no message
    ec.tick()
    assert(result.value.isDefined)
    assert(result.value.get.isFailure)
    assert(result.value.get.failed.get.isInstanceOf[StatusRuntimeException])
    assertEquals(dummy.messagesSent.size, 1)
    assertEquals(dummy.requested, 1)
  }

  test("error response to unaryToUnary") {
    implicit val ec = TestContext()

    val dummy  = new DummyClientCall()
    val client = new Fs2ClientCall[IO, String, Int](dummy)
    val result = client.unaryToUnaryCall("hello", new Metadata()).unsafeToFuture()
    dummy.listener.get.onMessage(5)

    dummy.listener.get.onClose(Status.INTERNAL, new Metadata())

    // Check that call completes after status but no message
    ec.tick()
    assert(result.value.isDefined)
    assert(result.value.get.isFailure)
    assert(result.value.get.failed.get.isInstanceOf[StatusRuntimeException])
    assertEquals(result.value.get.failed.get
                   .asInstanceOf[StatusRuntimeException]
                   .getStatus,
                 Status.INTERNAL)
    assertEquals(dummy.messagesSent.size, 1)
    assertEquals(dummy.requested, 1)
  }

  test("stream to streamingToUnary") {
    implicit val ec = TestContext()

    val dummy  = new DummyClientCall()
    val client = new Fs2ClientCall[IO, String, Int](dummy)
    val result = client
      .streamingToUnaryCall(Stream.emits(List("a", "b", "c")), new Metadata())
      .unsafeToFuture()

    dummy.listener.get.onMessage(5)

    // Check that call does not complete after result returns
    ec.tick()
    assertEquals(result.value, None)

    dummy.listener.get.onClose(Status.OK, new Metadata())

    // Check that call completes after status
    ec.tick()
    assertEquals(result.value, Some(Success(5)))
    assertEquals(dummy.messagesSent.size, 3)
    assertEquals(dummy.requested, 1)
  }

  test("0-length to streamingToUnary") {
    implicit val ec = TestContext()

    val dummy  = new DummyClientCall()
    val client = new Fs2ClientCall[IO, String, Int](dummy)
    val result = client
      .streamingToUnaryCall(Stream.empty, new Metadata())
      .unsafeToFuture()

    dummy.listener.get.onMessage(5)

    // Check that call does not complete after result returns
    ec.tick()
    assertEquals(result.value, None)

    dummy.listener.get.onClose(Status.OK, new Metadata())

    // Check that call completes after status
    ec.tick()
    assertEquals(result.value, Some(Success(5)))
    assertEquals(dummy.messagesSent.size, 0)
    assertEquals(dummy.requested, 1)
  }

  test("single message to unaryToStreaming") {
    implicit val ec = TestContext()

    val dummy  = new DummyClientCall()
    val client = new Fs2ClientCall[IO, String, Int](dummy)
    val result =
      client.unaryToStreamingCall("hello", new Metadata()).compile.toList.unsafeToFuture()
    dummy.listener.get.onMessage(1)
    dummy.listener.get.onMessage(2)
    dummy.listener.get.onMessage(3)

    // Check that call does not complete after result returns
    ec.tick()
    assertEquals(result.value, None)

    dummy.listener.get.onClose(Status.OK, new Metadata())

    // Check that call completes after status
    ec.tick()
    assertEquals(result.value, Some(Success(List(1, 2, 3))))
    assertEquals(dummy.messagesSent.size, 1)
    assertEquals(dummy.requested, 4)
  }

  test("stream to streamingToStreaming") {
    implicit val ec = TestContext()

    val dummy  = new DummyClientCall()
    val client = new Fs2ClientCall[IO, String, Int](dummy)
    val result =
      client
        .streamingToStreamingCall(Stream.emits(List("a", "b", "c", "d", "e")), new Metadata())
        .compile
        .toList
        .unsafeToFuture()
    dummy.listener.get.onMessage(1)
    dummy.listener.get.onMessage(2)
    dummy.listener.get.onMessage(3)

    // Check that call does not complete after result returns
    ec.tick()
    assertEquals(result.value, None)

    dummy.listener.get.onClose(Status.OK, new Metadata())

    // Check that call completes after status
    ec.tick()
    assertEquals(result.value, Some(Success(List(1, 2, 3))))
    assertEquals(dummy.messagesSent.size, 5)
    assertEquals(dummy.requested, 4)
  }

  test("error returned from streamingToStreaming") {
    implicit val ec = TestContext()

    val dummy  = new DummyClientCall()
    val client = new Fs2ClientCall[IO, String, Int](dummy)
    val result =
      client
        .streamingToStreamingCall(Stream.emits(List("a", "b", "c", "d", "e")), new Metadata())
        .compile
        .toList
        .unsafeToFuture()
    dummy.listener.get.onMessage(1)
    dummy.listener.get.onMessage(2)
    dummy.listener.get.onMessage(3)

    // Check that call does not complete after result returns
    ec.tick()
    assertEquals(result.value, None)

    dummy.listener.get.onClose(Status.INTERNAL, new Metadata())

    // Check that call completes after status
    ec.tick()
    assert(result.value.isDefined)
    assert(result.value.get.isFailure)
    assert(result.value.get.failed.get.isInstanceOf[StatusRuntimeException])
    assertEquals(result.value.get.failed.get
                   .asInstanceOf[StatusRuntimeException]
                   .getStatus,
                 Status.INTERNAL)
    assertEquals(dummy.messagesSent.size, 5)
    assertEquals(dummy.requested, 4)
  }

  test("stream awaits termination of managed channel") {
    implicit val ec = TestContext()

    import implicits._
    val result = ManagedChannelBuilder.forAddress("127.0.0.1", 0).stream[IO].compile.last.unsafeToFuture()

    ec.tick()
    val channel = result.value.get.get.get
    assert(channel.isTerminated)
  }
}
