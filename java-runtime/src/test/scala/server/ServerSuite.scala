package org.lyranthe.fs2_grpc
package java_runtime
package server

import cats.effect.{ContextShift, IO}
import cats.effect.laws.util.TestContext
import cats.implicits._
import fs2._
import io.grpc._
import minitest._

object ServerSuite extends SimpleTestSuite {

  test("single message to unaryToUnary") {

    implicit val ec: TestContext      = TestContext()
    implicit val cs: ContextShift[IO] = IO.contextShift(ec)

    val dummy    = new DummyServerCall
    val listener = Fs2UnaryServerCallListener[IO](dummy).unsafeRunSync()

    listener.unsafeUnaryResponse(new Metadata(), _.map(_.length))
    listener.onMessage("123")
    listener.onHalfClose()

    ec.tick()

    assertEquals(dummy.messages.size, 1)
    assertEquals(dummy.messages(0), 3)
    assertEquals(dummy.currentStatus.isDefined, true)
    assertEquals(dummy.currentStatus.get.isOk, true)
  }

  test("cancellation for unaryToUnary") {

    implicit val ec: TestContext      = TestContext()
    implicit val cs: ContextShift[IO] = IO.contextShift(ec)

    val dummy    = new DummyServerCall
    val listener = Fs2UnaryServerCallListener[IO](dummy).unsafeRunSync()

    listener.unsafeUnaryResponse(new Metadata(), _.map(_.length))
    listener.onCancel()

    val cancelled = listener.isCancelled.get.unsafeToFuture()

    ec.tick()

    assertEquals(cancelled.isCompleted, true)
  }

  test("multiple messages to unaryToUnary") {

    implicit val ec: TestContext      = TestContext()
    implicit val cs: ContextShift[IO] = IO.contextShift(ec)

    val dummy    = new DummyServerCall
    val listener = Fs2UnaryServerCallListener[IO](dummy).unsafeRunSync()

    listener.unsafeUnaryResponse(new Metadata(), _.map(_.length))
    listener.onMessage("123")

    intercept[StatusRuntimeException] {
      listener.onMessage("456")
    }

    listener.onHalfClose()

    ec.tick()

    assertEquals(dummy.currentStatus.isDefined, true)
    assertResult(true, "Current status true because stream completed successfully")(dummy.currentStatus.get.isOk)
  }

  test("resource awaits termination of server") {

    implicit val ec: TestContext = TestContext()
    import implicits._

    val result = ServerBuilder.forPort(0).resource[IO].use(IO.pure).unsafeToFuture()
    ec.tick()
    val server = result.value.get.get
    assert(server.isTerminated)
  }

  test("single message to unaryToStreaming") {

    implicit val ec: TestContext      = TestContext()
    implicit val cs: ContextShift[IO] = IO.contextShift(ec)

    val dummy    = new DummyServerCall
    val listener = Fs2UnaryServerCallListener[IO].apply[String, Int](dummy).unsafeRunSync()

    listener.unsafeStreamResponse(new Metadata(), s => Stream.eval(s).map(_.length).repeat.take(5))
    listener.onMessage("123")
    listener.onHalfClose()

    ec.tick()

    assertEquals(dummy.messages.size, 5)
    assertEquals(dummy.messages(0), 3)
    assertEquals(dummy.currentStatus.isDefined, true)
    assertEquals(dummy.currentStatus.get.isOk, true)
  }

  test("zero messages to streamingToStreaming") {

    implicit val ec: TestContext      = TestContext()
    implicit val cs: ContextShift[IO] = IO.contextShift(ec)

    val dummy    = new DummyServerCall
    val listener = Fs2StreamServerCallListener[IO].apply[String, Int](dummy).unsafeRunSync()

    listener.unsafeStreamResponse(new Metadata(), _ => Stream.emit(3).repeat.take(5))
    listener.onHalfClose()

    ec.tick()

    assertEquals(dummy.messages.size, 5)
    assertEquals(dummy.messages(0), 3)
    assertEquals(dummy.currentStatus.isDefined, true)
    assertEquals(dummy.currentStatus.get.isOk, true)
  }

  test("cancellation for streamingToStreaming") {

    implicit val ec: TestContext      = TestContext()
    implicit val cs: ContextShift[IO] = IO.contextShift(ec)


    val dummy    = new DummyServerCall
    val listener = Fs2StreamServerCallListener[IO].apply[String, Int](dummy).unsafeRunSync()

    listener.unsafeStreamResponse(new Metadata(), _ => Stream.emit(3).repeat.take(5))

    listener.onCancel()

    val cancelled = listener.isCancelled.get.unsafeToFuture()

    ec.tick()

    assertEquals(cancelled.isCompleted, true)
  }

  test("messages to streamingToStreaming") {

    implicit val ec: TestContext      = TestContext()
    implicit val cs: ContextShift[IO] = IO.contextShift(ec)

    val dummy    = new DummyServerCall
    val listener = Fs2StreamServerCallListener[IO].apply[String, Int](dummy).unsafeRunSync()

    listener.unsafeStreamResponse(new Metadata(), _.map(_.length).intersperse(0))
    listener.onMessage("a")
    listener.onMessage("ab")
    listener.onHalfClose()

    ec.tick()

    assertEquals(dummy.messages.length, 3)
    assertEquals(dummy.messages.toList, List(1, 0, 2))
    assertEquals(dummy.currentStatus.isDefined, true)
    assertEquals(dummy.currentStatus.get.isOk, true)
  }

  test("messages to streamingToStreaming") {

    implicit val ec: TestContext      = TestContext()
    implicit val cs: ContextShift[IO] = IO.contextShift(ec)

    val dummy    = new DummyServerCall
    val listener = Fs2StreamServerCallListener[IO].apply[String, Int](dummy).unsafeRunSync()

    listener.unsafeStreamResponse(
      new Metadata(),
      _.map(_.length) ++ Stream.emit(0) ++ Stream.raiseError[IO](new RuntimeException("hello")))
    listener.onMessage("a")
    listener.onMessage("ab")
    listener.onHalfClose()
    listener.onMessage("abc")

    ec.tick()

    assertEquals(dummy.messages.length, 3)
    assertEquals(dummy.messages.toList, List(1, 2, 0))
    assertEquals(dummy.currentStatus.isDefined, true)
    assertEquals(dummy.currentStatus.get.isOk, false)
  }

  test("streaming to unary") {

    implicit val ec: TestContext      = TestContext()
    implicit val cs: ContextShift[IO] = IO.contextShift(ec)

    val implementation: Stream[IO, String] => IO[Int] =
      _.compile.foldMonoid.map(_.length)

    val dummy    = new DummyServerCall
    val listener = Fs2StreamServerCallListener[IO].apply[String, Int](dummy).unsafeRunSync()

    listener.unsafeUnaryResponse(new Metadata(), implementation)
    listener.onMessage("ab")
    listener.onMessage("abc")
    listener.onHalfClose()

    ec.tick()

    assertEquals(dummy.messages.length, 1)
    assertEquals(dummy.messages(0), 5)
    assertEquals(dummy.currentStatus.isDefined, true)
    assertEquals(dummy.currentStatus.get.isOk, true)
  }

}