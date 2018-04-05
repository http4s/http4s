package org.lyranthe.fs2_grpc.java_runtime
package server

import cats.effect.IO
import cats.effect.laws.util.TestContext
//import cats.implicits._
//import fs2._
import io.grpc._
import minitest._

object ServerSuite extends SimpleTestSuite {
  test("single message to unaryToUnary") {
    implicit val ec = TestContext()
    val dummy = new DummyServerCall
    val listener = Fs2UnaryServerCallListener[IO].unsafeCreate(dummy)

    listener
      .unsafeUnaryResponse(new Metadata(), _.map(_.length))
    listener.onMessage("123")
    listener.onHalfClose()

    ec.tick()

    assertEquals(dummy.messages.size, 1)
    assertEquals(dummy.messages(0), 3)
    assertEquals(dummy.currentStatus.isDefined, true)
    assertEquals(dummy.currentStatus.get.isOk, true)
  }

  test("multiple messages to unaryToUnary") {
    implicit val ec = TestContext()
    val dummy = new DummyServerCall
    val listener = Fs2UnaryServerCallListener[IO].unsafeCreate(dummy)

    listener.unsafeUnaryResponse(new Metadata(), _.map(_.length))
    listener.onMessage("123")

    intercept[StatusRuntimeException] {
      listener.onMessage("456")
    }

    listener.onHalfClose()

    ec.tick()

    assertEquals(dummy.currentStatus.isDefined, true)
    assertResult(true,
                 "Current status true because stream completed successfully")(
      dummy.currentStatus.get.isOk)
  }

  test("stream awaits termination of server") {
    implicit val ec = TestContext()

    import implicits._
    val result = ServerBuilder.forPort(0).stream[IO].compile.last.unsafeToFuture()

    ec.tick()
    val server = result.value.get.get.get
    assert(server.isTerminated)
  }
  
//  test("single message to unaryToStreaming") {
//    val dummy = new DummyServerCall
//
//    val serverCall = new Fs2ServerCall[IO, String, Int](dummy)
//    val listener = serverCall
//      .unaryToStreamingCall(new Metadata(),
//                            s =>
//                              Stream
//                                .emit(s.length)
//                                .repeat
//                                .take(5))
//      .unsafeRunSync()
//    listener.onMessage("123")
//    listener.onHalfClose()
//    Thread.sleep(200)
//    assertEquals(dummy.messages.size, 5)
//    assertEquals(dummy.messages(0), 3)
//    assertEquals(dummy.currentStatus.isDefined, true)
//    assertEquals(dummy.currentStatus.get.isOk, true)
//  }
//
//  test("zero messages to streamingToStreaming") {
//    val dummy = new DummyServerCall
//
//    val serverCall = new Fs2ServerCall[IO, String, Int](dummy)
//    val listener = serverCall
//      .streamingToStreamingCall(new Metadata(),
//                                s => Stream.emit(3).repeat.take(5))
//      .unsafeRunSync()
//    listener.onHalfClose()
//    Thread.sleep(200)
//    assertEquals(dummy.messages.size, 5)
//    assertEquals(dummy.messages(0), 3)
//    assertEquals(dummy.currentStatus.isDefined, true)
//    assertEquals(dummy.currentStatus.get.isOk, true)
//  }
//
//  test("messages to streamingToStreaming") {
//    val dummy = new DummyServerCall
//
//    val serverCall = new Fs2ServerCall[IO, String, Int](dummy)
//    val listener = serverCall
//      .streamingToStreamingCall(new Metadata(), _.map(_.length).intersperse(0))
//      .unsafeRunSync()
//    listener.onMessage("a")
//    listener.onMessage("ab")
//    listener.onHalfClose()
//    Thread.sleep(400)
//    assertEquals(dummy.messages.length, 3)
//    assertEquals(dummy.messages.toList, List(1, 0, 2))
//    assertEquals(dummy.currentStatus.isDefined, true)
//    assertEquals(dummy.currentStatus.get.isOk, true)
//  }
//
//  test("messages to streamingToStreaming") {
//    val dummy = new DummyServerCall
//
//    val serverCall = new Fs2ServerCall[IO, String, Int](dummy)
//    val listener = serverCall
//      .streamingToStreamingCall(new Metadata(),
//                                _.map(_.length) ++ Stream.emit(0) ++ Stream
//                                  .raiseError(new RuntimeException("hello")))
//      .unsafeRunSync()
//    listener.onMessage("a")
//    listener.onMessage("ab")
//    listener.onHalfClose()
//    listener.onMessage("abc")
//    Thread.sleep(400)
//    assertEquals(dummy.messages.length, 3)
//    assertEquals(dummy.messages.toList, List(1, 2, 0))
//    assertEquals(dummy.currentStatus.isDefined, true)
//    assertEquals(dummy.currentStatus.get.isOk, false)
//  }
//
//  test("streaming to unary") {
//    val implementation: Stream[IO, String] => IO[Int] =
//      _.compile.foldMonoid.map(_.length)
//
//    val dummy = new DummyServerCall
//    val serverCall = new Fs2ServerCall[IO, String, Int](dummy)
//    val listener =
//      serverCall
//        .streamingToUnaryCall(new Metadata(), implementation)
//        .unsafeRunSync()
//
//    listener.onMessage("ab")
//    listener.onMessage("abc")
//    listener.onHalfClose()
//    Thread.sleep(100)
//    assertEquals(dummy.messages.length, 1)
//    assertEquals(dummy.messages(0), 5)
//    assertEquals(dummy.currentStatus.isDefined, true)
//    assertEquals(dummy.currentStatus.get.isOk, true)
//  }

}
