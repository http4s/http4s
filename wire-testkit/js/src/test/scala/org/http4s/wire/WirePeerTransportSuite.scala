/*
 * Copyright 2026 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s.wire

import cats.effect.Deferred
import cats.effect.IO
import cats.effect.Ref
import cats.effect.Resource
import cats.syntax.all._
import com.comcast.ip4s._
import fs2.Chunk
import scodec.bits.ByteVector

import scala.concurrent.duration._
import scala.scalajs.js

class WirePeerTransportSuite extends RuntimeDiagnostics.Suite {

  import WirePeerTransport._

  private val bytes = Chunk.array(Array[Byte](1, 2, 3, 4))
  private def pair: Resource[IO, (NativeSocket[IO], NativeSocket[IO])] =
    bindNative[IO].flatMap(listener =>
      (connectNative[IO](listener.address), listener.accept).parTupled
    )

  private def nativeError(code: String, syscall: String): js.Dynamic =
    js.Dynamic.literal(message = s"$syscall $code", code = code, syscall = syscall)

  private def inject(socket: NativeSocket[IO])(
      hook: js.Function3[js.Dynamic, String, js.Function1[js.Any, Unit], Unit]
  ): IO[Unit] = IO(socket.native.updateDynamic("_write")(hook))

  test("private Node hook behavioral compatibility: both directions and half-close") {
    verifyCompatibility[IO].timeout(10.seconds)
  }

  test("compatibility guard rejects a present hook that destroys input on callback failure") {
    verifyCompatibility[IO]((socket: NativeSocket[IO]) =>
      inject(socket) { (_, _, cb) =>
        val error = nativeError("EPIPE", "write")
        cb(error)
        socket.native.destroy(error)
        ()
      }
    ).attempt.map(result => assert(result.isLeft)).timeout(5.seconds)
  }

  for {
    accepted <- Vector(false, true)
    code <- Vector("EPIPE", "ECONNRESET")
  }
    test(s"isolated callback $code retains input and never invents EOF (accepted=$accepted)") {
      pair
        .use { case (connected, server) =>
          val (local, remote) = if (accepted) (server, connected) else (connected, server)
          for {
            trace <- Transcript.create[IO]()
            peer <- local.peer(trace)
            _ <- inject(local)((_, _, cb) => cb(nativeError(code, "write")))
            failed <- peer.write(ByteVector(9)).attempt
            _ = assert(failed.swap.exists(RuntimeInfo.isClosedWrite))
            _ = assert(!failed.swap.exists(RuntimeInfo.isConnectionReset))
            _ <- remote.write(bytes)
            _ <- peer.readExact(4).assertEquals(ByteVector(1, 2, 3, 4))
            held <- peer.awaitInput.as(true).timeoutTo(50.millis, IO.pure(false))
            _ = assert(!held, "write failure must not provide an input witness")
            _ <- remote.endOfOutput
            _ <- peer.awaitInput.assertEquals(false)
          } yield ()
        }
        .timeout(5.seconds)
    }

  test("native read-reset classifier requires read provenance") {
    IO {
      val read = new ReadFailure(nativeError("ECONNRESET", "read"))
      val write = new WriteFailure(nativeError("ECONNRESET", "write"))
      assert(RuntimeInfo.isConnectionReset(read))
      assert(!RuntimeInfo.isConnectionReset(write))
      assert(!RuntimeInfo.isConnectionReset(new java.io.IOException("outer", write)))
      assert(!RuntimeInfo.isConnectionReset(new ReadFailure(nativeError("ECONNRESET", "write"))))
      assert(!RuntimeInfo.isConnectionReset(new ReadFailure(nativeError("EPIPE", "read"))))
      assert(RuntimeInfo.isClosedWrite(write))
      assert(!RuntimeInfo.isClosedWrite(new WriteFailure(nativeError("ECONNRESET", "read"))))
      assert(!RuntimeInfo.isClosedWrite(new WriteFailure(nativeError("EIO", "write"))))
      read.addSuppressed(new IllegalStateException("independent input error"))
      assert(!RuntimeInfo.isConnectionReset(read))
    }
  }

  private def graphFailure(kind: String): Throwable = kind match {
    case "adapter" => new AdapterFailure("marker graph fixture", new RuntimeException("failed"))
    case "timeout" => new java.util.concurrent.TimeoutException("deadline")
    case _ => new IllegalStateException("independent marker failure")
  }

  private def addGraphFailure(marker: Throwable, placement: String, extra: Throwable): IO[Unit] =
    IO {
      assert(marker.getSuppressed.isEmpty)
      assert(marker.getCause.getSuppressed.isEmpty)
      assertEquals(marker.getCause.getCause, null)
      placement match {
        case "marker-suppressed" => marker.addSuppressed(extra)
        case "cause-suppressed" => marker.getCause.addSuppressed(extra)
        case _ => val _ = marker.getCause.initCause(extra)
      }
      if (placement != "marker-suppressed") assert(marker.getSuppressed.isEmpty)
    }

  private def assertCleanNativeMarker(marker: Throwable, native: js.Any): Unit = {
    assert(marker.getSuppressed.isEmpty)
    marker.getCause match {
      case cause @ js.JavaScriptException(value) =>
        assert(value == native)
        assertEquals(cause.getCause, null)
        assert(cause.getSuppressed.isEmpty)
      case other => fail(s"expected native marker cause, got $other")
    }
  }

  private def assertGraphFailure(
      write: WriteFailure,
      extra: Throwable,
      kind: String,
      contextualRead: Boolean,
  ): IO[Unit] =
    Http2RejectionWrite.reply(IO.raiseError[Unit](write)).attempt.map { result =>
      assert(!RuntimeInfo.isClosedWrite(write))
      assert(!RuntimeInfo.isConnectionReset(write))
      if (contextualRead || kind == "unrelated")
        assert(result.swap.exists(_ eq write), result.toString)
      else if (kind == "adapter") assert(result.swap.exists(_ eq extra), result.toString)
      else
        assert(
          result.swap.exists(error =>
            error.isInstanceOf[java.util.concurrent.TimeoutException] && (error.getCause eq write)
          ),
          result.toString,
        )
    }

  for {
    code <- Vector("EPIPE", "ECONNRESET")
    placement <- Vector("marker-suppressed", "cause-suppressed", "deeper-cause")
    kind <- Vector("unrelated", "adapter", "timeout")
  } test(s"native write marker preserves $code/$placement/$kind graph") {
    for {
      write <- IO(new WriteFailure(nativeError(code, "write")))
      _ = assertCleanNativeMarker(write, write.nativeError)
      _ = assert(RuntimeInfo.isClosedWrite(write))
      _ = assert(!RuntimeInfo.isConnectionReset(write))
      _ <- Http2RejectionWrite.reply(IO.raiseError[Unit](write)).assertEquals(false)
      extra = graphFailure(kind)
      _ <- addGraphFailure(write, placement, extra)
      _ <- assertGraphFailure(write, extra, kind, contextualRead = false)
    } yield ()
  }

  for {
    code <- Vector("EPIPE", "ECONNRESET", "ERR_SOCKET_CLOSED")
    marker <- Vector("write", "read")
    placement <- Vector("marker-suppressed", "cause-suppressed", "deeper-cause")
    kind <- Vector("unrelated", "adapter", "timeout")
  } test(s"contextual native marker preserves $code/$marker/$placement/$kind graph") {
    pair
      .use { case (local, remote) =>
        for {
          _ <- resetAfterBuffering(local, remote)
          _ <- inject(local)((_, _, cb) =>
            cb(
              if (code == "ERR_SOCKET_CLOSED") js.Dynamic.literal(code = code)
              else nativeError(code, "write")
            )
          )
          failed <- local.write(bytes).attempt
          write <- failed match {
            case Left(write: WriteFailure) => IO.pure(write)
            case other =>
              IO.raiseError[WriteFailure](new IllegalStateException(s"expected marker: $other"))
          }
          read <- IO(write.precedingReadReset.getOrElse(fail("expected independent read reset")))
          _ = assertCleanNativeMarker(write, write.nativeError)
          _ = assertCleanNativeMarker(read, read.nativeError)
          _ = assert(RuntimeInfo.isConnectionReset(read))
          _ = assert(!RuntimeInfo.isClosedWrite(read))
          _ = assert(RuntimeInfo.isClosedWrite(write))
          _ <- Http2RejectionWrite.reply(IO.raiseError[Unit](write)).assertEquals(false)
          extra = graphFailure(kind)
          _ <- addGraphFailure(if (marker == "read") read else write, placement, extra)
          _ = assert(write.precedingReadReset.exists(_ eq read))
          _ = assertEquals(RuntimeInfo.isConnectionReset(read), marker != "read")
          _ <- assertGraphFailure(write, extra, kind, contextualRead = marker == "read")
        } yield ()
      }
      .timeout(5.seconds)
  }

  test("scripted closed-write fixture carries native write provenance") {
    Http2WriteFailureFixture.closedWrite.flatMap { error =>
      IO {
        error match {
          case write: WriteFailure =>
            assertEquals(nativeCode(write.nativeError, "code"), Some("EPIPE"))
            assertEquals(nativeCode(write.nativeError, "syscall"), Some("write"))
            assertEquals(write.precedingReadReset, None)
            write.getCause match {
              case js.JavaScriptException(native) => assert(native == write.nativeError)
              case other => fail(s"expected native write cause, got $other")
            }
          case other => fail(s"expected scripted write failure, got $other")
        }
        assert(RuntimeInfo.isClosedWrite(error))
        assert(!RuntimeInfo.isConnectionReset(error))
      } *> Http2RejectionWrite.reply(IO.raiseError[Unit](error)).assertEquals(false)
    }
  }

  test("scripted closed-write fixture isolates repeated evaluations of the same effect") {
    val fixture = Http2WriteFailureFixture.closedWrite
    val extra = new IllegalStateException("independent fixture failure")
    for {
      first <- fixture
      _ <- IO {
        first.addSuppressed(extra)
        first.getCause.addSuppressed(extra)
      }
      second <- fixture
      _ <- IO {
        assert(!(first eq second))
        assert(!(first.getCause eq second.getCause))
        (first, second) match {
          case (left: WriteFailure, right: WriteFailure) =>
            assert(left.nativeError != right.nativeError)
          case other => fail(s"expected scripted write failures, got $other")
        }
        assertEquals(first.getSuppressed.toVector, Vector(extra))
        assertEquals(first.getCause.getSuppressed.toVector, Vector(extra))
        assert(second.getSuppressed.isEmpty)
        assert(second.getCause.getSuppressed.isEmpty)
        assert(!RuntimeInfo.isClosedWrite(first))
        assert(RuntimeInfo.isClosedWrite(second))
        assert(!RuntimeInfo.isConnectionReset(second))
      }
      result <- Http2RejectionWrite.reply(IO.raiseError[Unit](first)).attempt
      _ = assert(result.swap.exists(_ eq first), result.toString)
      _ <- Http2RejectionWrite.reply(IO.raiseError[Unit](second)).assertEquals(false)
    } yield ()
  }

  test("unknown callback failure stays infrastructure and does not erase input failure") {
    pair.use { case (local, _) =>
      for {
        _ <- inject(local)((_, _, cb) => cb(nativeError("EIO", "write")))
        error <- local.write(bytes).attempt
        _ = assert(error.isLeft)
        _ = assert(!error.swap.exists(RuntimeInfo.isClosedWrite))
        _ <- IO { local.native.emit("error", nativeError("EIO", "read")); () }
        read <- local.read(1).attempt
        _ = assert(read.swap.exists(_.isInstanceOf[ReadFailure]))
        _ = assert(!read.swap.exists(RuntimeInfo.isConnectionReset))
      } yield ()
    }
  }

  test("canceling a submitted write retains serialization until its native callback") {
    pair
      .use { case (local, remote) =>
        var callbacks = Vector.empty[js.Function1[js.Any, Unit]]
        val hook: js.Function3[js.Dynamic, String, js.Function1[js.Any, Unit], Unit] =
          (_, _, cb) => callbacks = callbacks :+ cb
        def awaitCount(n: Int): IO[Unit] =
          IO.defer(if (callbacks.size >= n) IO.unit else IO.cede *> awaitCount(n))
        for {
          _ <- inject(local)(hook)
          first <- local.write(bytes).start
          _ <- awaitCount(1)
          _ <- first.cancel
          second <- local.write(bytes).start
          _ <- IO.cede.replicateA_(10)
          _ = assertEquals(callbacks.size, 1)
          _ <- remote.write(bytes)
          _ <- local.read(4).map(_.map(_.toVector)).assertEquals(Some(bytes.toVector))
          _ <- IO(callbacks.head(js.undefined))
          _ <- awaitCount(2)
          _ <- IO(callbacks(1)(js.undefined))
          _ <- second.joinWithNever
        } yield ()
      }
      .timeout(5.seconds)
  }

  test("canceling a waiting write does not submit it or lose the active callback") {
    pair
      .use { case (local, _) =>
        var callback: Option[js.Function1[js.Any, Unit]] = None
        var count = 0
        def awaitWrite: IO[Unit] = IO.defer(if (count > 0) IO.unit else IO.cede *> awaitWrite)
        for {
          _ <- inject(local) { (_, _, cb) => count += 1; callback = Some(cb) }
          first <- local.write(bytes).start
          _ <- awaitWrite
          _ <- first.cancel
          waiting <- local.write(bytes).start
          _ <- IO.cede
          _ <- waiting.cancel
          _ = assertEquals(count, 1)
          _ <- IO(callback.get(js.undefined))
        } yield ()
      }
      .timeout(5.seconds)
  }

  test("output shutdown waits for a canceled submitted write to settle") {
    pair
      .use { case (local, remote) =>
        var callback: Option[js.Function1[js.Any, Unit]] = None
        def submitted: IO[Unit] = IO.defer(if (callback.nonEmpty) IO.unit else IO.cede *> submitted)
        for {
          _ <- inject(local) { (_, _, cb) => callback = Some(cb) }
          writing <- local.write(bytes).start
          _ <- submitted
          _ <- writing.cancel
          ending <- local.endOfOutput.start
          _ <- IO.cede.replicateA_(10)
          _ <- IO(local.native.writableEnded.asInstanceOf[Boolean]).assertEquals(false)
          _ <- IO(callback.get(js.undefined))
          _ <- ending.joinWithNever
          _ <- remote.read(1).assertEquals(None)
        } yield ()
      }
      .timeout(5.seconds)
  }

  test("read cancellation preserves bytes and leaves exactly the owned listeners") {
    pair
      .use { case (local, remote) =>
        for {
          listeners <- IO(local.native.listenerCount("readable").asInstanceOf[Int])
          reading <- local.read(4).start
          _ <- IO.cede
          _ <- reading.cancel
          _ <- remote.write(bytes)
          _ <- local.read(2).map(_.map(_.toVector)).assertEquals(Some(Vector[Byte](1, 2)))
          _ <- local.read(2).map(_.map(_.toVector)).assertEquals(Some(Vector[Byte](3, 4)))
          _ <- IO(local.native.listenerCount("readable").asInstanceOf[Int]).assertEquals(listeners)
        } yield ()
      }
      .timeout(5.seconds)
  }

  test(
    "local output half-close permits continued input and rejects local writes as infrastructure"
  ) {
    pair
      .use { case (local, remote) =>
        for {
          _ <- local.endOfOutput
          _ <- remote.read(1).assertEquals(None)
          failed <- local.write(bytes).attempt
          _ = assert(failed.isLeft)
          _ = assert(!failed.swap.exists(RuntimeInfo.isClosedWrite))
          _ <- remote.write(bytes)
          _ <- local.read(4).map(_.map(_.toVector)).assertEquals(Some(bytes.toVector))
          _ <- remote.endOfOutput
          _ <- local.read(1).assertEquals(None)
        } yield ()
      }
      .timeout(5.seconds)
  }

  test("close without an independent native input event fails; release never returns EOF") {
    pair
      .use { case (local, _) =>
        for {
          _ <- IO { local.native.destroy(); () }
          error <- local.read(1).attempt
          _ = assert(error.isLeft)
          _ = assert(!error.swap.exists(RuntimeInfo.isConnectionReset))
          _ <- local.release
          released <- local.read(1).attempt
          _ = assert(released.isLeft)
        } yield ()
      }
      .timeout(5.seconds)
  }

  test("release wakes a pending read with infrastructure failure and removes listeners") {
    pair
      .use { case (local, _) =>
        for {
          nativeEndListeners <- IO(local.native.listenerCount("end").asInstanceOf[Int] - 1)
          read <- local.read(1).attempt.start
          _ <- IO.cede
          _ <- local.release
          result <- read.joinWithNever
          _ = assert(result.isLeft)
          _ <- IO(local.native.listenerCount("readable").asInstanceOf[Int]).assertEquals(0)
          _ <- IO(local.native.listenerCount("end").asInstanceOf[Int])
            .assertEquals(nativeEndListeners)
          _ <- IO(local.native.listenerCount("error").asInstanceOf[Int]).assertEquals(0)
          _ <- IO(local.native.listenerCount("close").asInstanceOf[Int]).assertEquals(0)
        } yield ()
      }
      .timeout(5.seconds)
  }

  test("release during a submitted write fails it and tolerates its late callback") {
    pair
      .use { case (local, _) =>
        var callback: Option[js.Function1[js.Any, Unit]] = None
        def submitted: IO[Unit] = IO.defer(if (callback.nonEmpty) IO.unit else IO.cede *> submitted)
        for {
          _ <- inject(local) { (_, _, cb) => callback = Some(cb) }
          writing <- local.write(bytes).attempt.start
          _ <- submitted
          _ <- local.release
          result <- writing.joinWithNever
          _ = assert(result.isLeft)
          _ = assert(!result.swap.exists(RuntimeInfo.isClosedWrite))
          _ <- IO(callback.get(nativeError("EPIPE", "write")))
        } yield ()
      }
      .timeout(5.seconds)
  }

  test("read sizes remain bounded and RawPeer budget failures remain failures") {
    pair
      .use { case (local, remote) =>
        for {
          _ <- remote.write(Chunk.array(Array.fill[Byte](20000)(42)))
          one <- local.read(1)
          _ = assertEquals(one.map(_.size), Some(1))
          invalid <- local.read(0).attempt
          _ = assert(invalid.isLeft)
          trace <- Transcript.create[IO]()
          peer <- local.peer(trace)
          _ <- peer.readToEnd(32).intercept[WireObservationLimit]
        } yield ()
      }
      .timeout(5.seconds)
  }

  for {
    accepted <- Vector(false, true)
    partial <- Vector(false, true)
  }
    test(
      s"full close drains malformed or partial tails before terminal (accepted=$accepted, partial=$partial)"
    ) {
      pair
        .use { case (connected, server) =>
          val (local, remote) = if (accepted) (server, connected) else (connected, server)
          val tail =
            if (partial) ByteVector(0, 0, 4, 8, 0, 0, 0)
            else
              Http2Wire.encode(Http2Wire.Frame(Http2Wire.WindowUpdate, 0, 0, Http2Wire.uint32(0)))
          for {
            trace <- Transcript.create[IO]()
            peer <- local.peer(trace)
            _ <- remote.write(Chunk.byteVector(tail))
            _ <- remote.release
            observed <- peer.readToEnd(1024, allowReset = true)
            _ = assertEquals(observed, tail)
          } yield ()
        }
        .timeout(5.seconds)
    }

  test("a genuine native read reset is independent of the write callback") {
    pair
      .use { case (local, remote) =>
        for {
          _ <- IO {
            if (js.typeOf(remote.native.resetAndDestroy) != "function")
              fail("Node resetAndDestroy is required for the native reset compatibility test")
            remote.native.resetAndDestroy()
            ()
          }
          result <- local.read(1).attempt
          _ = assert(result.swap.exists(RuntimeInfo.isConnectionReset), result.toString)
          _ = assert(!result.swap.exists(RuntimeInfo.isClosedWrite))
        } yield ()
      }
      .timeout(5.seconds)
  }

  test("unclaimed accepted connection closes when the listener is released") {
    bindNative[IO].allocated
      .flatMap { case (listener, release) =>
        connectNative[IO](listener.address)
          .use { remote =>
            remote.write(bytes) *> release *> remote.read(1).attempt.map { result =>
              assert(result == Right(None) || result.swap.exists(RuntimeInfo.isConnectionReset))
            }
          }
          .guarantee(release)
      }
      .timeout(5.seconds)
  }

  test("canceling an empty accept leaves the listener available for its connection") {
    bindNative[IO]
      .use { listener =>
        for {
          waiting <- listener.accept.use(_ => IO.never[Unit]).start
          _ <- IO.cede
          _ <- waiting.cancel
          _ <- (connectNative[IO](listener.address), listener.accept).parTupled.use {
            case (remote, local) =>
              remote.write(bytes) *> local
                .read(4)
                .map(_.map(_.toVector))
                .assertEquals(Some(bytes.toVector))
          }
        } yield ()
      }
      .timeout(5.seconds)
  }

  test("cancellation racing native acquisition releases every allocated listener and socket") {
    IO.defer {
      bindNative[IO].use { listener =>
        for {
          connecting <- connectNative[IO](listener.address).use(_ => IO.never[Unit]).start
          _ <- IO.cede
          _ <- connecting.cancel
        } yield ()
      }
    }.replicateA_(30)
      .timeout(5.seconds)
  }

  test("listener owns unclaimed connections and canceled accept cleanup") {
    for {
      allocated <- bindNative[IO].allocated
      (listener, release) = allocated
      _ <- connectNative[IO](listener.address)
        .use { remote =>
          for {
            accept <- listener.accept.use(_ => IO.never[Unit]).start
            _ <- IO.cede
            _ <- accept.cancel
            _ <- release
            result <- remote.read(1).attempt
            _ = assert(result == Right(None) || result.swap.exists(RuntimeInfo.isConnectionReset))
          } yield ()
        }
        .guarantee(release)
      rebound <- bindNative[IO].use(_ => IO.unit).attempt
      _ = assert(rebound.isRight)
    } yield ()
  }

  private def awaitBuffered(socket: NativeSocket[IO], size: Int): IO[Unit] =
    IO.defer {
      if (socket.native.readableLength.asInstanceOf[Int] >= size) IO.unit
      else IO.cede *> awaitBuffered(socket, size)
    }

  private def resetAfterBuffering(local: NativeSocket[IO], remote: NativeSocket[IO]): IO[Unit] =
    IO.async[Unit] { cb =>
      IO {
        if (js.typeOf(remote.native.resetAndDestroy) != "function")
          throw new IllegalStateException("Node resetAndDestroy is required")
        val closed: js.Function0[Unit] = () => cb(Right(()))
        local.native.once("close", closed)
        remote.native.resetAndDestroy()
        Some(IO { local.native.removeListener("close", closed); () })
      }
    }

  // Exercise the unchanged catalog observer. The read gate only arranges the native
  // reset before its first rejection read; it does not supply bytes or a verdict.
  for {
    accepted <- Vector(false, true)
    control <- Vector("none", "settings", "ping")
    tail <- Vector("valid", "malformed", "partial")
  } test(s"buffered control after real read reset: $accepted/$control/$tail") {
    import Http2Wire._
    pair
      .use { case (connected, server) =>
        val (local, remote) = if (accepted) (server, connected) else (connected, server)
        val controls = control match {
          case "none" => ByteVector.empty
          case "settings" => encode(settings())
          case _ => encode(Frame(Ping, 0, 0, ByteVector.fill(8)(0)))
        }
        val suffix = tail match {
          case "malformed" => encode(Frame(WindowUpdate, 0, 0, uint32(0)))
          case "partial" => ByteVector(0, 0, 4, 8, 0, 0, 0)
          case _ => ByteVector.empty
        }
        val target = new WireTarget[IO] {
          def withFailureObserver(observe: AdapterFailure => IO[Unit]): WireTarget[IO] = this

          val name = "native-reset-order"
          val version = "1"
          def server(protocol: Protocol, scenario: ServerScenario): Resource[IO, ServerHandle[IO]] =
            Resource.pure(
              ServerHandle[IO](
                SocketAddress(ip"127.0.0.1", port"1"),
                IO.never[RequestData],
                IO.pure(None),
              )
            )
          def client(
              protocol: Protocol,
              address: SocketAddress[IpAddress],
          ): Resource[IO, ClientDriver[IO]] =
            Resource.eval(IO.raiseError(new UnsupportedOperationException("server only")))
        }
        for {
          trace <- Transcript.create[IO]()
          sent <- Ref.of[IO, Boolean](false)
          reset <- Deferred[IO, Unit]
          failures <- Ref.of[IO, Vector[Throwable]](Vector.empty)
          peer <- RawPeer.create[IO](
            (size: Int) =>
              sent.get.flatMap(done => (if (done) reset.get else IO.unit) *> local.read(size)),
            (chunk: Chunk[Byte]) =>
              local.write(chunk).attempt.flatMap {
                case Left(error) => failures.update(_ :+ error) *> IO.raiseError[Unit](error)
                case Right(_) =>
                  if (chunk.size >= 9 && chunk(3) == Headers.toByte) sent.set(true) else IO.unit
              },
            local.endOfOutput,
            trace,
          )
          remoteTrace <- Transcript.create[IO]()
          remotePeer <- remote.peer(remoteTrace)
          script = Http2PeerSettings.scoped[IO, Unit](initiatesRequests = false) {
            implicit settings =>
              serverHandshake(remotePeer) *>
                readUntil(remotePeer, fromServer = false)(_.frameType == Headers).void *>
                remote.write(Chunk.byteVector(controls ++ suffix)) *>
                awaitBuffered(local, 9 + (controls ++ suffix).size.toInt) *>
                resetAfterBuffering(local, remote) *> reset.complete(()).void
          }
          wireCase = Http2MessageCases.all.find(_.id == "h2.server.reject-uppercase-field-name").get
          result <- (
            script,
            wireCase
              .run(
                CaseContext(
                  target,
                  trace,
                  (_, _) => Resource.pure(peer),
                )
              )
              .attempt,
          ).parTupled.map(_._2)
          writes <- failures.get
          _ =
            if (tail == "valid") assertEquals(result, Right(()))
            else if (tail == "partial")
              assert(result.swap.exists(_.isInstanceOf[ReadFailure]), result.toString)
            else assert(result.swap.exists(_.isInstanceOf[ProtocolFailure]), result.toString)
          _ = assertEquals(writes.size, if (control == "none") 0 else 1)
          _ = writes.foreach { error =>
            assert(RuntimeInfo.isClosedWrite(error), error.toString)
            assert(!RuntimeInfo.isConnectionReset(error))
            assertEquals(
              nativeCode(error.asInstanceOf[WriteFailure].nativeError, "code"),
              Some("ERR_SOCKET_CLOSED"),
            )
          }
        } yield ()
      }
      .timeout(5.seconds)
  }

  for {
    accepted <- Vector(false, true)
    mode <- Vector(
      "held-open",
      "local-release",
      "released-after-reset",
      "unknown-read",
      "unknown-write",
      "suppressed-read",
    )
  } test(s"closed socket callback requires independent read-reset provenance: $accepted/$mode") {
    pair
      .use { case (connected, server) =>
        val (local, remote) = if (accepted) (server, connected) else (connected, server)
        for {
          _ <- mode match {
            case "local-release" => local.release
            case "released-after-reset" => resetAfterBuffering(local, remote) *> local.release
            case "suppressed-read" =>
              resetAfterBuffering(local, remote) *> IO {
                local.native.emit("error", nativeError("EIO", "read"))
                ()
              }
            case "unknown-read" =>
              IO {
                local.native.emit("error", nativeError("EIO", "read"))
                local.native.destroy()
                ()
              }
            case "unknown-write" => resetAfterBuffering(local, remote)
            case _ => IO.unit
          }
          _ <- inject(local)((_, _, cb) =>
            cb(
              if (mode == "unknown-write") nativeError("EIO", "write")
              else js.Dynamic.literal(code = "ERR_SOCKET_CLOSED")
            )
          )
          result <- Http2RejectionWrite.reply(local.write(bytes)).attempt
          _ = assert(result.isLeft, result.toString)
          _ = assert(!result.swap.exists(RuntimeInfo.isClosedWrite), result.toString)
          _ <-
            if (mode == "held-open")
              local.read(1).as(true).timeoutTo(50.millis, IO.pure(false)).assertEquals(false)
            else IO.unit
        } yield ()
      }
      .timeout(5.seconds)
  }

  test("generic ERR_SOCKET_CLOSED has no read-reset provenance") {
    IO {
      val error = js.Dynamic.literal(code = "ERR_SOCKET_CLOSED")
      assert(!RuntimeInfo.isClosedWrite(js.JavaScriptException(error)))
      assert(!RuntimeInfo.isClosedWrite(new WriteFailure(error)))
    }
  }

  for (kind <- Vector("adapter", "timeout", "unrelated"))
    test(s"read-reset contextual callback preserves suppressed $kind failure") {
      pair
        .use { case (local, remote) =>
          for {
            _ <- resetAfterBuffering(local, remote)
            failed <- local.write(bytes).attempt
            error <- failed match {
              case Left(error) => IO.pure(error)
              case Right(_) =>
                IO.raiseError[Throwable](new IllegalStateException("expected callback failure"))
            }
            _ = assert(RuntimeInfo.isClosedWrite(error))
            extra = kind match {
              case "adapter" =>
                new AdapterFailure("reset-order fixture", new RuntimeException("failed"))
              case "timeout" => new java.util.concurrent.TimeoutException("deadline")
              case _ => new IllegalStateException("unrelated")
            }
            _ <- IO(error.addSuppressed(extra))
            result <- Http2RejectionWrite.reply(IO.raiseError[Unit](error)).attempt
            _ = assert(result.isLeft, result.toString)
            _ =
              if (kind == "adapter") assert(result.swap.exists(_ eq extra))
              else if (kind == "timeout")
                assert(result.swap.exists(_.isInstanceOf[java.util.concurrent.TimeoutException]))
              else assert(result.swap.exists(_ eq error))
          } yield ()
        }
        .timeout(5.seconds)
    }
}
