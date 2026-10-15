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
import com.comcast.ip4s._
import fs2.Chunk
import scodec.bits.ByteVector

class Http2BufferedCompletionSuite extends RuntimeDiagnostics.Suite {
  private sealed abstract class Closure(label: String) {
    override def toString: String = label
  }
  private object Closure {
    case object Writable extends Closure("writable")
    case object PostHead extends Closure("post-head")
    case object FirstData extends Closure("first-data")
    case object MiddleData extends Closure("middle-data")
    case object FinalData extends Closure("final-data")
    case object PartialCredit extends Closure("partial-credit")
  }

  private sealed abstract class Ending(label: String) {
    override def toString: String = label
  }
  private object Ending {
    case object Data extends Ending("data")
    case object Trailers extends Ending("trailers")
    case object ContinuedTrailers extends Ending("continued-trailers")
    case object WrongBody extends Ending("wrong-body")
    case object MissingEnd extends Ending("missing-end")
    case object TruncatedFrame extends Ending("truncated-frame")
    case object HeldIncomplete extends Ending("held-incomplete")
    case object BadHpack extends Ending("bad-hpack")
    case object BadTrailers extends Ending("bad-trailers")
    case object BadSettings extends Ending("bad-settings")
    case object BadPing extends Ending("bad-ping")
    case object InterruptedTrailers extends Ending("interrupted-trailers")
    case object Oversize extends Ending("oversize")
  }

  private sealed abstract class Fault(label: String, val adapter: Boolean, val timeout: Boolean) {
    override def toString: String = label
  }
  private object Fault {
    case object Closed extends Fault("closed", false, false)
    case object UnrelatedIo extends Fault("unrelated-io", false, false)
    case object WrappedClose extends Fault("wrapped-close", false, false)
    case object Timeout extends Fault("timeout", false, true)
    case object Adapter extends Fault("adapter", true, false)
    case object SuppressedTimeout extends Fault("suppressed-timeout", false, true)
    case object SuppressedAdapter extends Fault("suppressed-adapter", true, false)
    case object WrappedSuppressedAdapter extends Fault("wrapped-suppressed-adapter", true, false)
    case object SuppressedAdapterTimeout extends Fault("suppressed-adapter-timeout", true, true)
  }

  private final class CompletionCase(val id: String, val stateCase: Boolean, val extraAcks: Int) {
    override def toString: String = id
  }
  private val stateCompletion =
    new CompletionCase("h2.server.peer-concurrency-zero-allows-responses", true, 0)
  private val bufferedCompletionCases = Vector(
    new CompletionCase("h2.server.default-frame-size-splits-data", false, 0),
    new CompletionCase("h2.server.settings-replace-frame-limit", false, 2),
    stateCompletion,
  )

  for {
    id <- bufferedCompletionCases
    closure <- Vector[Closure](
      Closure.Writable,
      Closure.PostHead,
      Closure.FirstData,
      Closure.MiddleData,
      Closure.FinalData,
      Closure.PartialCredit,
    )
    ending <- Vector[Ending](Ending.Data, Ending.Trailers, Ending.ContinuedTrailers)
    padded <- Vector(false, true)
  } test(s"H2 buffered completion: legal/$id/$closure/$ending/padded=$padded") {
    checkBufferedCompletion(id, closure, ending, padded)
  }

  for {
    id <- bufferedCompletionCases
    ending <- Vector[Ending](
      Ending.WrongBody,
      Ending.MissingEnd,
      Ending.TruncatedFrame,
      Ending.HeldIncomplete,
      Ending.BadHpack,
      Ending.BadTrailers,
      Ending.BadSettings,
      Ending.BadPing,
      Ending.InterruptedTrailers,
    ) ++
      (if (id.stateCase) Vector.empty
       else Vector[Ending](Ending.Oversize))
  } test(s"H2 buffered completion: invalid/$id/$ending") {
    checkBufferedCompletion(id, Closure.PostHead, ending, padded = true)
  }

  for {
    id <- bufferedCompletionCases
    fault <- Vector[Fault](
      Fault.UnrelatedIo,
      Fault.WrappedClose,
      Fault.Timeout,
      Fault.Adapter,
      Fault.SuppressedTimeout,
      Fault.SuppressedAdapter,
      Fault.WrappedSuppressedAdapter,
      Fault.SuppressedAdapterTimeout,
    )
  } test(s"H2 buffered completion: provenance/$id/$fault") {
    checkBufferedCompletion(id, Closure.PostHead, Ending.Trailers, padded = false, fault)
  }

  for (closure <- Vector[Closure](Closure.Writable, Closure.PostHead, Closure.PartialCredit))
    test(s"H2 buffered completion: State attempted credit/$closure") {
      checkBufferedCompletion(
        stateCompletion,
        closure,
        Ending.Trailers,
        padded = true,
        exhaustInitialCredit = true,
      )
    }

  private def checkBufferedCompletion(
      id: CompletionCase,
      closure: Closure,
      ending: Ending,
      padded: Boolean,
      fault: Fault = Fault.Closed,
      exhaustInitialCredit: Boolean = false,
  ): IO[Unit] = {
    import Http2Wire._
    for {
      trace <- Transcript.create[IO]()
      failedWrites <- Ref.of[IO, Int](0)
      deliveredCredit <- Ref.of[IO, ByteVector](ByteVector.empty)
      readBlocked <- Deferred[IO, Unit]
      released <- Deferred[IO, Unit]
      completed <- Deferred[IO, Either[Throwable, Unit]]
      stateCase = id.stateCase
      expected =
        if (stateCase) Http1Wire.ascii("abcdefghijklmnop") else ByteVector.fill(40000)(42.toByte)
      body = if (ending == Ending.WrongBody) expected.dropRight(1) ++ ByteVector(43) else expected
      head = encode(headers(1, Vector(":status" -> "200"), false))
      setup = encode(settings()) ++ encode(settings(ack = true)) ++
        Vector.fill(id.extraAcks)(encode(settings(ack = true))).foldLeft(ByteVector.empty)(_ ++ _)
      chunks = body.grouped(if (stateCase) 6 else 16000).toVector
      frames = (if (exhaustInitialCredit)
                  Vector.fill(256)(
                    Frame(Data, Padded, 1, ByteVector(255.toByte) ++ ByteVector.fill(255)(0))
                  )
                else Vector.empty) ++ chunks.zipWithIndex.map { case (bytes, index) =>
        val flags = if (ending == Ending.Data && index == chunks.size - 1) EndStream else 0
        if (padded) Frame(Data, flags | Padded, 1, ByteVector(7) ++ bytes ++ ByteVector.fill(7)(0))
        else data(1, bytes, flags == EndStream)
      }
      // State grants precede DATA; subsequent closure positions use an incidental PING.
      // Control positions exercise the actual payload-credit write after that DATA.
      closeIndex = closure match {
        case Closure.MiddleData => 1
        case Closure.FinalData => 2
        case Closure.Writable | Closure.PostHead | Closure.FirstData | Closure.PartialCredit => 0
      }
      ping = encode(Frame(Ping, 0, 0, ByteVector.fill(8)(1)))
      prefixData = frames.take(closeIndex).foldLeft(ByteVector.empty)((b, f) => b ++ encode(f))
      closingPing =
        if (stateCase && closure != Closure.PostHead && closure != Closure.PartialCredit) ping
        else ByteVector.empty
      tailData = frames.drop(closeIndex).foldLeft(ByteVector.empty)((b, f) => b ++ encode(f))
      control = ending match {
        case Ending.BadSettings => encode(settings(Vector(2 -> 1L)))
        case Ending.BadPing => encode(Frame(Ping, 0, 0, ByteVector(1)))
        case Ending.Oversize => encode(Frame(0xff, 0, 0, ByteVector.fill(16385)(0)))
        case Ending.Data | Ending.Trailers | Ending.ContinuedTrailers | Ending.WrongBody |
            Ending.MissingEnd | Ending.TruncatedFrame | Ending.HeldIncomplete | Ending.BadHpack |
            Ending.BadTrailers | Ending.InterruptedTrailers =>
          encode(settings(ack = true)) ++ encode(settings()) ++ ping
      }
      trailers = ending match {
        case Ending.Data | Ending.MissingEnd | Ending.HeldIncomplete => ByteVector.empty
        case Ending.TruncatedFrame => encode(headers(1, Vector("x-end" -> "yes"))).dropRight(1)
        case Ending.BadHpack =>
          encode(Frame(Headers, EndHeaders | EndStream, 1, ByteVector(0x80.toByte)))
        case Ending.BadTrailers => encode(headers(1, Vector(":status" -> "200")))
        case Ending.ContinuedTrailers | Ending.InterruptedTrailers =>
          val block = headers(1, Vector("x-end" -> "yes")).payload
          encode(Frame(Headers, EndStream, 1, block.take(1))) ++
            (if (ending == Ending.InterruptedTrailers) ping else ByteVector.empty) ++
            encode(Frame(Continuation, EndHeaders, 1, block.drop(1)))
        case Ending.Trailers | Ending.WrongBody | Ending.BadSettings | Ending.BadPing |
            Ending.Oversize =>
          encode(headers(1, Vector("x-end" -> "yes")))
      }
      // Place validated housekeeping before END_STREAM, including after the failed grant.
      response =
        if (ending == Ending.Data)
          head ++ prefixData ++ closingPing ++ tailData
        else head ++ prefixData ++ closingPing ++ tailData ++ control ++ trailers
      shutdown =
        if (
          Set[Ending](Ending.MissingEnd, Ending.HeldIncomplete, Ending.TruncatedFrame).contains(
            ending
          )
        )
          ByteVector.empty
        else encode(Frame(GoAway, 0, 0, uint32(1) ++ uint32(0)))
      input <- Ref.of[IO, ByteVector](setup ++ response ++ shutdown)
      readCount <- Ref.of[IO, Long](0L)
      closeAt = setup.size + (if (closure == Closure.PostHead || closure == Closure.PartialCredit)
                                1L
                              else head.size + prefixData.size + 1L)
      closed <- Http2WriteFailureFixture.closedWrite
      timeout = new java.util.concurrent.TimeoutException("completion write timeout")
      adapter = new AdapterFailure("completion fixture", closed)
      error <- IO {
        fault match {
          case Fault.UnrelatedIo => new java.io.IOException("unrelated device failure")
          case Fault.WrappedClose => new java.io.IOException("unknown wrapper", closed)
          case Fault.Timeout => timeout
          case Fault.Adapter => adapter
          case Fault.SuppressedTimeout => closed.addSuppressed(timeout); closed
          case Fault.SuppressedAdapter => closed.addSuppressed(adapter); closed
          case Fault.SuppressedAdapterTimeout =>
            closed.addSuppressed(timeout); closed.addSuppressed(adapter); closed
          case Fault.WrappedSuppressedAdapter =>
            val wrapper = new RuntimeException("wrapper", closed)
            wrapper.addSuppressed(adapter)
            wrapper
          case Fault.Closed => closed
        }
      }
      read = (n: Int) =>
        input
          .modify { bytes =>
            val (next, rest) = bytes.splitAt(n.toLong)
            (rest, next)
          }
          .flatMap { bytes =>
            readCount.update(_ + bytes.size) *>
              (if (bytes.nonEmpty) IO.pure(Some(Chunk.byteVector(bytes)))
               else if (ending == Ending.HeldIncomplete)
                 readBlocked.complete(()).void *> IO.never[Option[Chunk[Byte]]]
               else IO.pure(None))
          }
      write = (bytes: Chunk[Byte]) =>
        readCount.get.flatMap { count =>
          if (closure == Closure.Writable || count < closeAt) IO.unit
          else if (closure == Closure.PartialCredit) deliveredCredit.get.flatMap { delivered =>
            // Control writes both grants together; State writes them separately.
            // Model one complete grant reaching the peer before the transport fails.
            (if (delivered.isEmpty) deliveredCredit.set(ByteVector(bytes.toArray).take(13))
             else IO.unit) *>
              (if (stateCase && delivered.isEmpty) IO.unit
               else failedWrites.update(_ + 1) *> IO.raiseError[Unit](error))
          }
          else failedWrites.update(_ + 1) *> IO.raiseError[Unit](error)
        }
      peer <- RawPeer.create(read, write, IO.unit, trace)
      target = ScriptedServer[IO](
        "buffered-completion",
        Resource.pure(
          ServerHandle[IO](
            SocketAddress(ip"127.0.0.1", port"1"),
            IO.never[RequestData],
            IO.pure(None),
          )
        ),
      )
      run = Catalog.all
        .find(_.id == id.id)
        .get
        .run(
          CaseContext(
            target,
            trace,
            (_, _) => Resource.make(IO.pure(peer))(_ => released.complete(()).void),
          )
        )
      _ <-
        if (ending == Ending.HeldIncomplete) {
          run.attempt.flatMap(completed.complete).start.flatMap { fiber =>
            IO.race(readBlocked.get, completed.get)
              .flatMap {
                case Left(_) => completed.tryGet.assertEquals(None)
                case Right(result) =>
                  IO(fail(s"incomplete response completed before its held read: $result"))
              }
              .guarantee(fiber.cancel)
          }
        } else
          run.attempt.flatMap { result =>
            IO {
              if (fault != Fault.Closed) {
                val expectedFailure =
                  if (fault.adapter) adapter
                  else if (fault.timeout) timeout
                  else error
                if (fault == Fault.SuppressedTimeout)
                  assert(
                    result.swap.exists(e =>
                      e.isInstanceOf[java.util.concurrent.TimeoutException] && (e.getCause eq error)
                    ),
                    result.toString,
                  )
                else assert(result.swap.exists(_ eq expectedFailure), result.toString)
              } else if (exhaustInitialCredit && closure == Closure.PostHead)
                assert(result.swap.exists(_.isInstanceOf[ProtocolFailure]), result.toString)
              else if (
                Set[Ending](Ending.Data, Ending.Trailers, Ending.ContinuedTrailers).contains(ending)
              )
                assertEquals(result, Right(()))
              else
                assert(
                  result.swap.exists(e =>
                    e.isInstanceOf[ProtocolFailure] || e.isInstanceOf[WireDecodingFailure]
                  ),
                  result.toString,
                )
            }
          }
      _ <- released.tryGet.assertEquals(Some(()))
      writes <- failedWrites.get
      _ <- IO {
        val finalDataGuard =
          !stateCase && ending == Ending.Data && closure == Closure.FinalData
        assertEquals(writes, if (closure == Closure.Writable || finalDataGuard) 0 else 1)
      }
      _ <-
        if (closure == Closure.PartialCredit) deliveredCredit.get.flatMap { bytes =>
          IO.fromEither(decodeAll(bytes)).flatMap { grants =>
            IO { assertEquals(grants.size, 1); assertEquals(grants.head.frameType, WindowUpdate) }
          }
        }
        else IO.unit
    } yield ()
  }

}
