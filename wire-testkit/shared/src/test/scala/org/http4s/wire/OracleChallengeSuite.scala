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

import cats.effect.Async
import cats.effect.Deferred
import cats.effect.IO
import cats.effect.Outcome
import cats.effect.Ref
import cats.effect.Resource
import cats.effect.std.Queue
import cats.effect.syntax.all._
import cats.syntax.all._
import com.comcast.ip4s._
import fs2.io.net.Network
import munit.CatsEffectSuite
import scodec.bits.ByteVector

import scala.concurrent.duration._

class OracleChallengeSuite extends CatsEffectSuite {
  // Scripted peers intentionally emit invalid traffic; conformance cases supply their own scope.
  implicit private def codecSettings[F[_]: cats.ApplicativeThrow]: Http2PeerSettings[F] =
    Http2PeerSettings.untracked[F]

  override val munitIOTimeout: Duration = 15.seconds

  private val accepted = Http1Wire.ascii("accepted")

  test("the GET smoke oracle accepts the correct scripted response and releases its peer") {
    runHttp1Challenge(accepted, 8, None)
  }

  test("the GET smoke oracle rejects incorrect content at the expected body assertion") {
    val wrong = Http1Wire.ascii("rejected")
    runHttp1Challenge(wrong, 8, Some(wrong))
  }

  test("the GET smoke oracle detects a body delimited one octet too early") {
    runHttp1Challenge(accepted, 7, Some(accepted.take(7)))
  }

  test("the GET smoke oracle classifies malformed received Content-Length as protocol failure") {
    for {
      fixture <- ScriptedTarget.create[IO] { (peer, publish) =>
        readAndPublish(peer, publish) *> peer.write(
          Http1Wire.message("HTTP/1.1 200 OK", Vector("Content-Length" -> "eight"), accepted)
        )
      }
      (target, probe) = fixture
      transcript <- Transcript.create[IO]()
      outcome <- catalogCase("h1.server.get").run(CaseContext(target, transcript)).attempt
      _ <- IO {
        assertEquals(WireSuite.outcomeStatus(outcome), "fail")
        assert(outcome.left.toOption.exists(_.isInstanceOf[WireDecodingFailure]))
        assertEquals(outcome.left.map(_.getMessage), Left("invalid framing length"))
      }
      _ <- assertReleased(probe)
    } yield ()
  }

  test("malformed received wire with an adapter release failure is a blocking error") {
    val cleanup = new AdapterFailure("fixture cleanup", new java.io.IOException("cleanup"))
    for {
      fixture <- ScriptedTarget.create[IO] { (peer, publish) =>
        readAndPublish(peer, publish) *> peer.write(
          Http1Wire.message("HTTP/1.1 200 OK", Vector("Content-Length" -> "eight"), accepted)
        )
      }
      (underlying, probe) = fixture
      target = new WireTarget[IO] {
        def withFailureObserver(observe: AdapterFailure => IO[Unit]): WireTarget[IO] = this

        val name = underlying.name
        val version = underlying.version
        def server(protocol: Protocol, scenario: ServerScenario): Resource[IO, ServerHandle[IO]] =
          AdapterFailure.guardRelease(
            underlying
              .server(protocol, scenario)
              .onFinalize(IO.raiseError[Unit](cleanup)),
            "scripted cleanup",
          )
        def client(
            protocol: Protocol,
            address: SocketAddress[IpAddress],
        ): Resource[IO, ClientDriver[IO]] =
          underlying.client(protocol, address)
      }
      transcript <- Transcript.create[IO]()
      outcome <- catalogCase("h1.server.get").run(CaseContext(target, transcript)).attempt
      _ <- IO(assertEquals(WireSuite.outcomeStatus(outcome), "error"))
      _ <- IO(assert(outcome.left.exists(_.isInstanceOf[WireDecodingFailure])))
      _ <- assertReleased(probe)
    } yield ()
  }

  test("the PING smoke oracle accepts an identical payload and releases its peer") {
    runPingChallenge(identity, shouldFail = false)
  }

  test("the PING smoke oracle rejects a corrupted echo at the expected payload assertion") {
    runPingChallenge(
      payload => ByteVector((payload(0) ^ 1).toByte) ++ payload.drop(1),
      shouldFail = true,
    )
  }

  test("the HPACK rejection oracle accepts a connection compression error and releases its peer") {
    runConnectionScopeChallenge(connectionError = true)
  }

  test("the HPACK rejection oracle rejects a connection-error GOAWAY placed on a stream") {
    // RFC 9113 section 6.8: GOAWAY is connection-scoped and requires stream ID zero.
    // Only the frame's stream placement changes; COMPRESSION_ERROR and closure stay identical.
    runConnectionScopeChallenge(connectionError = true, wrongStreamId = true)
  }

  test("the stream-only compression-error challenge remains an observation gap, not a violation") {
    runConnectionScopeChallenge(connectionError = false)
  }

  test(
    "the stream credit oracle accepts padded DATA within credit and resumes after WINDOW_UPDATE"
  ) {
    runStreamCreditChallenge(countPadding = true)
  }

  test("the stream credit oracle rejects counting only content octets in padded DATA") {
    runStreamCreditChallenge(countPadding = false)
  }

  test("canceling a waiting smoke case releases the scripted socket, listener, and fiber") {
    for {
      waiting <- Deferred[IO, Unit]
      fixture <- ScriptedTarget.create[IO] { (peer, publish) =>
        readAndPublish(peer, publish) *> waiting.complete(()).void *> IO.never[Unit]
      }
      (target, probe) = fixture
      transcript <- Transcript.create[IO]()
      run = AdapterFailure.observe(target)(monitored =>
        catalogCase("h1.server.get").run(CaseContext(monitored, transcript))
      )
      _ <- Resource.make(run.start)(_.cancel).use { fiber =>
        for {
          _ <- waiting.get
          _ <- fiber.cancel
          outcome <- fiber.join
          _ <- outcome match {
            case Outcome.Canceled() => IO.unit
            case Outcome.Errored(error) => IO.raiseError(error)
            case Outcome.Succeeded(result) => result *> IO(fail("waiting smoke case completed"))
          }
          _ <- assertReleased(probe)
        } yield ()
      }
    } yield ()
  }

  test("case deadline reports cleanup failure after cancellation and releases every resource") {
    val cleanup = new AdapterFailure("scripted cleanup", new java.io.IOException("cleanup"))
    for {
      fixture <- ScriptedTarget.create[IO]((peer, publish) => readAndPublish(peer, publish))
      (underlying, probe) = fixture
      target = new CleanupTarget(underlying, cleanup, None)
      transcript <- Transcript.create[IO]()
      result <- WireSuite
        .runCase(target, catalogCase("h1.server.get"), transcript, 1.second)
        .attempt
      _ <- IO(assert(result.left.exists(_.getCause eq cleanup)))
      _ <- IO(
        assert(
          result.left.exists(
            _.getSuppressed.exists(
              _.isInstanceOf[java.util.concurrent.TimeoutException]
            )
          )
        )
      )
      _ <- IO(assertEquals(WireSuite.outcomeStatus(result), "error"))
      _ <- assertReleased(probe)
      // The next observation scope on the same binding starts with an empty journal.
      _ <- AdapterFailure.observe(target)(_ => IO.unit)
    } yield ()
  }

  private final class CleanupTarget(
      underlying: WireTarget[IO],
      cleanup: AdapterFailure,
      observer: Option[AdapterFailure => IO[Unit]],
  ) extends WireTarget[IO] {
    val name = underlying.name
    val version = underlying.version
    override def withFailureObserver(observe: AdapterFailure => IO[Unit]): WireTarget[IO] =
      new CleanupTarget(underlying, cleanup, Some(observe))
    def server(protocol: Protocol, scenario: ServerScenario): Resource[IO, ServerHandle[IO]] =
      AdapterFailure.guardRelease(
        underlying.server(protocol, scenario).onFinalize(IO.raiseError[Unit](cleanup)),
        "scripted cleanup",
        observer,
      )
    def client(
        protocol: Protocol,
        address: SocketAddress[IpAddress],
    ): Resource[IO, ClientDriver[IO]] = underlying.client(protocol, address)
  }

  private def runHttp1Challenge(
      body: ByteVector,
      declaredLength: Int,
      expectedFailureBody: Option[ByteVector],
  ): IO[Unit] =
    for {
      fixture <- ScriptedTarget.create[IO] { (peer, publish) =>
        readAndPublish(peer, publish) *> peer.write(
          Http1Wire.message(
            "HTTP/1.1 200 OK",
            Vector("Content-Length" -> declaredLength.toString),
            body,
          )
        )
      }
      (target, probe) = fixture
      transcript <- Transcript.create[IO]()
      run = catalogCase("h1.server.get").run(CaseContext(target, transcript))
      _ <- expectedFailureBody match {
        case None => run
        case Some(observed) =>
          run.intercept[ProtocolFailure].flatMap { error =>
            IO(assertEquals(error.getMessage, s"expected: $accepted\nobserved: $observed"))
          }
      }
      _ <- assertReleased(probe)
    } yield ()

  private def runPingChallenge(
      transform: ByteVector => ByteVector,
      shouldFail: Boolean,
  ): IO[Unit] =
    for {
      payloads <- Deferred[IO, (ByteVector, ByteVector)]
      fixture <- ScriptedTarget.create[IO] { (peer, _) =>
        for {
          _ <- Http2Wire.serverHandshake(peer)
          ping <- Http2Wire.readUntil(peer, fromServer = false)(frame =>
            frame.frameType == Http2Wire.Ping && !frame.hasFlag(Http2Wire.Ack)
          )
          echoed = transform(ping.payload)
          _ <- payloads.complete(ping.payload -> echoed)
          _ <- Http2Wire.writeFrame(peer, ping.copy(flags = Http2Wire.Ack, payload = echoed))
        } yield ()
      }
      (target, probe) = fixture
      transcript <- Transcript.create[IO]()
      run = catalogCase("h2.server.ping").run(CaseContext(target, transcript))
      _ <-
        if (shouldFail)
          for {
            error <- run.intercept[ProtocolFailure]
            values <- payloads.get
            _ <- IO(
              assertEquals(error.getMessage, s"expected: ${values._1}\nobserved: ${values._2}")
            )
          } yield ()
        else run
      _ <- assertReleased(probe)
    } yield ()

  private def runConnectionScopeChallenge(
      connectionError: Boolean,
      wrongStreamId: Boolean = false,
  ): IO[Unit] = {
    val ping = Http2Wire.Frame(Http2Wire.Ping, 0, 0, ByteVector(3, 5, 7, 11, 13, 17, 19, 23))
    val failure =
      if (wrongStreamId) Some("invalid GOAWAY")
      else if (connectionError) None
      else Some("no connection error within the frame bound")
    for {
      progress <- Deferred[IO, Unit]
      _ <- runCatalogChallenge(
        "h2.server.hpack-reject-index-zero",
        failure,
        observationLimit = !connectionError,
      ) { (peer, _) =>
        for {
          _ <- Http2Wire.serverHandshake(peer)
          request <- Http2Wire.readUntil(peer, fromServer = false)(_.frameType == Http2Wire.Headers)
          _ <- IO(assertEquals(request.streamId, 1))
          _ <- IO(assert(request.hasFlag(Http2Wire.EndHeaders)))
          _ <- IO(assertEquals(request.payload.last, 0x80.toByte))
          _ <-
            if (connectionError)
              Http2Wire.writeFrame(
                peer,
                Http2Wire.Frame(
                  Http2Wire.GoAway,
                  0,
                  if (wrongStreamId) request.streamId else 0,
                  Http2Wire.uint32(0) ++ Http2Wire.uint32(9),
                ),
              ) *> peer.endOfOutput
            else
              for {
                // Preserve COMPRESSION_ERROR; change only its scope and leave transport open.
                _ <- Http2Wire.writeFrame(
                  peer,
                  Http2Wire.Frame(Http2Wire.RstStream, 0, request.streamId, Http2Wire.uint32(9)),
                )
                _ <- Http2Wire.writeFrame(peer, ping)
                acknowledgment <- Http2Wire.readUntil(peer, fromServer = false)(frame =>
                  frame.frameType == Http2Wire.Ping && frame.hasFlag(Http2Wire.Ack)
                )
                _ <- IO(assertEquals(acknowledgment, ping.copy(flags = Http2Wire.Ack)))
                _ <- progress.complete(())
                // Valid extension frames reach the existing finite observation bound. No
                // malformed frame, wrong error code, EOF, or timeout supplies a verdict.
                // Finite progress cannot prove the connection will never close: this
                // challenge exposes a coverage gap rather than killing the mutant.
                extension = Http2Wire.encode(Http2Wire.Frame(0xff, 0, 0, ByteVector.empty))
                _ <- peer.write(Vector.fill(1024)(extension).foldLeft(ByteVector.empty)(_ ++ _))
              } yield ()
        } yield ()
      }
      _ <- if (connectionError) IO.unit else progress.tryGet.assertEquals(Some(()))
    } yield ()
  }

  private def runStreamCreditChallenge(countPadding: Boolean): IO[Unit] = {
    val body = Http1Wire.ascii("abcdefghijklmnop")
    val firstContentBytes = if (countPadding) 1L else 3L
    val failure =
      if (countPadding) None else Some("DATA payload 5 exceeds remaining credit 3")
    for {
      resumed <- Deferred[IO, Unit]
      _ <- runCatalogChallenge("h2.server.stream-credit-boundary", failure) { (peer, _) =>
        for {
          _ <- Http2Wire.serverHandshake(peer)
          request <- Http2Wire.readUntil(peer, fromServer = false)(_.frameType == Http2Wire.Headers)
          _ <- IO(assertEquals(request.streamId, 1))
          _ <- Http2Wire.writeFrame(
            peer,
            Http2Wire.headers(request.streamId, Vector(":status" -> "200"), endStream = false),
          )
          // Initial credit is three octets. The pad-length field and one zero pad byte
          // consume two of them, so only the correct peer restricts initial content to one.
          _ <- Http2Wire.writeFrame(
            peer,
            Http2Wire.Frame(
              Http2Wire.Data,
              Http2Wire.Padded,
              request.streamId,
              ByteVector(1) ++ body.take(firstContentBytes) ++ ByteVector(0),
            ),
          )
          _ <-
            if (countPadding)
              for {
                ping <- Http2Wire.readUntil(peer, fromServer = false)(frame =>
                  frame.frameType == Http2Wire.Ping && !frame.hasFlag(Http2Wire.Ack)
                )
                _ <- Http2Wire.writeFrame(peer, ping.copy(flags = Http2Wire.Ack))
                budget = (body.size - firstContentBytes + 4096L * 256L).toInt
                connectionUpdate <- Http2Wire.readFrame(peer)
                _ <- IO(assertEquals(connectionUpdate, Http2Wire.windowUpdate(0, budget)))
                update <- Http2Wire.readFrame(peer)
                _ <- IO(assertEquals(update, Http2Wire.windowUpdate(request.streamId, budget)))
                _ <- resumed.complete(())
                _ <- Http2Wire.writeFrame(
                  peer,
                  Http2Wire.data(request.streamId, body.drop(firstContentBytes)),
                )
              } yield ()
            else IO.unit // Keep the scripted peer alive without racing failure cleanup with a read.
        } yield ()
      }
      _ <- if (countPadding) resumed.tryGet.assertEquals(Some(())) else IO.unit
    } yield ()
  }

  private def runCatalogChallenge(
      id: String,
      expectedFailure: Option[String],
      observationLimit: Boolean = false,
  )(
      script: ScriptedTarget.Script[IO]
  ): IO[Unit] =
    for {
      fixture <- ScriptedTarget.create[IO](script)
      (target, probe) = fixture
      transcript <- Transcript.create[IO]()
      run = catalogCase(id).run(CaseContext(target, transcript))
      _ <- expectedFailure match {
        case None => run
        case Some(message) if observationLimit =>
          run.intercept[WireObservationLimit].flatMap { error =>
            IO {
              assertEquals(error.getMessage, new WireObservationLimit(message).getMessage)
              assertEquals(WireSuite.outcomeStatus(Left(error)), "error")
            }
          }
        case Some(message) =>
          run
            .intercept[ProtocolFailure]
            .flatMap(error => IO(assertEquals(error.getMessage, message)))
      }
      _ <- assertReleased(probe)
    } yield ()

  private def readAndPublish(
      peer: RawPeer[IO],
      publish: RequestData => IO[Unit],
  ): IO[Unit] =
    Http1Wire.readRequest(peer).flatMap { request =>
      publish(RequestData(request.method, request.target, request.headers, request.body))
    }

  private def catalogCase(id: String): WireCase =
    Catalog.all
      .find(_.id == id)
      .getOrElse(throw new IllegalArgumentException(s"missing case: $id"))

  private def assertReleased(probe: ScriptedTarget.Probe[IO]): IO[Unit] =
    for {
      _ <- probe.accepted.tryGet.assertEquals(Some(()))
      _ <- probe.listenerFinalizers.get.assertEquals(1)
      _ <- probe.socketFinalizers.get.assertEquals(1)
      _ <- probe.scriptFinalizers.get.assertEquals(1)
    } yield ()
}

/** A test-only target for challenging case oracles with deliberately controlled wire output. */
private[wire] final class ScriptedTarget[F[_]: Async: Network] private (
    script: ScriptedTarget.Script[F],
    probe: ScriptedTarget.Probe[F],
) extends WireTarget[F] {
  def withFailureObserver(observe: AdapterFailure => F[Unit]): WireTarget[F] = this

  val name: String = "scripted"
  val version: String = "oracle-challenge"

  def server(_protocol: Protocol, _scenario: ServerScenario): Resource[F, ServerHandle[F]] =
    for {
      received <- Resource.eval(Queue.unbounded[F, RequestData])
      transcript <- Resource.eval(Transcript.create[F]())
      listener <- Network[F]
        .bind(SocketAddress(ip"127.0.0.1", port"0"))
        .onFinalize(probe.listenerFinalizers.update(_ + 1))
      serve = listener.accept.head.compile.resource.lastOrError
        .onFinalize(probe.socketFinalizers.update(_ + 1))
        .use { socket =>
          RawPeer.create(socket, transcript).flatMap { peer =>
            (probe.accepted.complete(()).void *> script(peer, received.offer) *>
              Async[F].never[Unit]).guarantee(probe.scriptFinalizers.update(_ + 1))
          }
        }
      _ <- Resource.make(serve.start) { fiber =>
        fiber.cancel *> fiber.join.flatMap {
          case Outcome.Errored(error) => Async[F].raiseError[Unit](error)
          case _ => Async[F].unit
        }
      }
    } yield ServerHandle(listener.address.asIpUnsafe, received.take, received.tryTake)

  def client(
      _protocol: Protocol,
      _address: SocketAddress[IpAddress],
  ): Resource[F, ClientDriver[F]] =
    Resource.eval(
      Async[F].raiseError[ClientDriver[F]](
        new UnsupportedOperationException("server-only scripted target")
      )
    )
}

private[wire] object ScriptedTarget {

  type Script[F[_]] = (RawPeer[F], RequestData => F[Unit]) => F[Unit]

  final case class Probe[F[_]](
      accepted: Deferred[F, Unit],
      listenerFinalizers: Ref[F, Int],
      socketFinalizers: Ref[F, Int],
      scriptFinalizers: Ref[F, Int],
  )

  def create[F[_]: Async: Network](script: Script[F]): F[(ScriptedTarget[F], Probe[F])] =
    for {
      accepted <- Deferred[F, Unit]
      listenerFinalizers <- Ref.of[F, Int](0)
      socketFinalizers <- Ref.of[F, Int](0)
      scriptFinalizers <- Ref.of[F, Int](0)
      probe = Probe(accepted, listenerFinalizers, socketFinalizers, scriptFinalizers)
    } yield (new ScriptedTarget[F](script, probe), probe)
}
