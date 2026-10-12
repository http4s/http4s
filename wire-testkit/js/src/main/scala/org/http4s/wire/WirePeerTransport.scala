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
import cats.effect.Resource
import cats.effect.std.Semaphore
import cats.syntax.all._
import com.comcast.ip4s._
import fs2.Chunk

import scala.concurrent.duration._
import scala.scalajs.js
import scala.scalajs.js.typedarray._

/** The scripted reference peer only. Targets continue to use their ordinary FS2 transport. */
private[wire] object WirePeerTransport {
  private lazy val net = js.Dynamic.global.require("node:net")
  private lazy val buffer = js.Dynamic.global.require("node:buffer").Buffer
  private val ReadSize = 8192

  final class WriteFailure private[WirePeerTransport] (
      val nativeError: js.Any,
      val precedingReadReset: Option[ReadFailure],
  ) extends java.io.IOException(
        "reference peer native write callback failed",
        js.JavaScriptException(nativeError),
      ) {
    def this(nativeError: js.Any) = this(nativeError, None)
  }
  final class ReadFailure(val nativeError: js.Any)
      extends java.io.IOException(
        "reference peer native input failed",
        js.JavaScriptException(nativeError),
      )

  private def localFailure(message: String): Throwable =
    new java.io.IOException(s"reference peer: $message")

  def nativeCode(value: js.Any, field: String): Option[String] =
    if (value == null || js.typeOf(value) != "object") None
    else {
      val found = value.asInstanceOf[js.Dynamic].selectDynamic(field)
      if (js.typeOf(found) == "string") Some(found.asInstanceOf[String]) else None
    }

  final case class Listener[F[_]](
      address: SocketAddress[IpAddress],
      accept: Resource[F, RawPeer[F]],
  )

  def connect[F[_]: Async](
      address: SocketAddress[IpAddress],
      transcript: Transcript[F],
  ): Resource[F, RawPeer[F]] =
    Resource.eval(compatible[F]).flatMap(_ => connectNative[F](address)).evalMap(_.peer(transcript))

  def bind[F[_]: Async](transcript: Transcript[F]): Resource[F, Listener[F]] =
    Resource.eval(compatible[F]).flatMap(_ => bindNative[F]).map { listener =>
      Listener(listener.address, listener.accept.evalMap(_.peer(transcript)))
    }

  /** All mutable state below is confined to synchronous JavaScript event-loop turns.
    * Input remains in Node's bounded non-flowing readable buffer: no background queue or pump.
    * Waiters only announce readiness; consuming/copying bytes is masked against cancellation.
    */
  final class NativeSocket[F[_]] private[WirePeerTransport] (
      val native: js.Dynamic,
      readLock: Semaphore[F],
      writeLock: Semaphore[F],
  )(implicit F: Async[F]) {
    private var terminal: Option[Either[Throwable, Unit]] = None
    private var released = false
    private var closeSeen = false
    private var outputEnded = false
    private var readWaiter: Option[Either[Throwable, Unit] => Unit] = None
    private var idleWaiter: Option[Either[Throwable, Unit] => Unit] = None
    private final class Pending(var callback: Option[Either[Throwable, Unit] => Unit])
    private var pending: Option[Pending] = None

    private def wakeRead(): Unit = {
      val waiter = readWaiter
      readWaiter = None
      waiter.foreach(_(Right(())))
    }
    private def wakeWriter(): Unit = {
      val waiter = idleWaiter
      idleWaiter = None
      waiter.foreach(_(Right(())))
    }
    private def failRead(error: Throwable): Unit = {
      terminal match {
        case Some(Left(previous)) => previous.addSuppressed(error)
        case _ => terminal = Some(Left(error))
      }
      wakeRead()
    }
    private val readable: js.Function0[Unit] = () => wakeRead()
    private val ended: js.Function0[Unit] = () => {
      if (terminal.isEmpty) terminal = Some(Right(()))
      wakeRead()
    }
    private val failed: js.Function1[js.Any, Unit] = error => failRead(new ReadFailure(error))
    private val closed: js.Function0[Unit] = () => {
      closeSeen = true
      if (terminal.isEmpty) failRead(localFailure("closed without an independent input witness"))
      wakeRead()
    }

    private[WirePeerTransport] def install(): Unit = {
      if (js.typeOf(native._write) != "function" || !native.allowHalfOpen.asInstanceOf[Boolean])
        throw localFailure("unsupported Node socket implementation")
      native.on("error", failed)
      native.on("end", ended)
      native.on("close", closed)
      native.on("readable", readable)
      ()
    }

    private def available: Int = native.readableLength.asInstanceOf[Int]
    private def ready: Boolean = released || available > 0 || terminal.nonEmpty

    private def awaitInput: F[Unit] = F.async[Unit] { cb =>
      F.delay {
        if (ready) cb(Right(()))
        else {
          readWaiter = Some(cb)
          // Install the waiter before requesting native input. No bytes are consumed here.
          native.read(0)
        }
        Some(F.delay { if (readWaiter.contains(cb)) readWaiter = None })
      }
    }

    def read(maxBytes: Int): F[Option[Chunk[Byte]]] =
      if (maxBytes <= 0) F.raiseError(new IllegalArgumentException("nonpositive native read size"))
      else
        readLock.permit.use { _ =>
          def loop: F[Option[Chunk[Byte]]] = F.uncancelable { poll =>
            poll(awaitInput) *> F.defer {
              if (released) F.raiseError(localFailure("input resource released"))
              else if (available > 0) F.delay {
                val count = math.min(math.min(maxBytes, ReadSize), available)
                val data = native.read(count).asInstanceOf[Uint8Array]
                if (data == null) throw localFailure("readable buffer disappeared")
                val bytes = new Array[Byte](data.length)
                var i = 0
                while (i < bytes.length) { bytes(i) = data(i).toByte; i += 1 }
                Some(Chunk.array(bytes))
              }
              else
                terminal match {
                  case Some(Left(error)) => F.raiseError(error)
                  case Some(Right(_)) => F.pure(None)
                  case None => poll(F.defer(loop))
                }
            }
          }
          loop
        }

    private def awaitIdle: F[Unit] = F.async[Unit] { cb =>
      F.delay {
        if (released) cb(Left(localFailure("output resource released")))
        else if (pending.isEmpty) cb(Right(()))
        else idleWaiter = Some(cb)
        Some(F.delay { if (idleWaiter.contains(cb)) idleWaiter = None })
      }
    }

    def write(bytes: Chunk[Byte]): F[Unit] = writeLock.permit.use { _ =>
      F.uncancelable { poll =>
        poll(awaitIdle) *> poll(F.async[Unit] { cb =>
          F.delay {
            if (released || outputEnded)
              cb(Left(localFailure("write after local output shutdown or release")))
            else {
              val operation = new Pending(Some(cb))
              val data = buffer.from(byteArray2Int8Array(bytes.toArray).buffer)
              pending = Some(operation)
              val complete: js.Function1[js.Any, Unit] = error =>
                if (pending.contains(operation)) {
                  pending = None
                  val callback = operation.callback
                  operation.callback = None
                  wakeWriter()
                  callback.foreach(
                    _(
                      if (error == null || js.isUndefined(error)) Right(())
                      else {
                        // A read reset can destroy Node's handle while readable bytes remain.
                        // Retain that independently observed terminal, never infer it from the
                        // callback. The reader still owns and must drain all buffered input.
                        val readReset =
                          if (!released && !outputEnded && native._handle == null)
                            terminal.collect {
                              case Left(read: ReadFailure) if RuntimeInfo.isConnectionReset(read) =>
                                read
                            }
                          else None
                        Left(new WriteFailure(error, readReset))
                      }
                    )
                  )
                }
              try
                // UNSUPPORTED PRIVATE NODE API: public Socket.write/FS2 Writable destroys
                // both directions on a write failure. Only this direct hook keeps its native
                // callback separate. Never call it concurrently, while connecting, or after end.
                // The bounded real-socket compatibility probe below gates every process; the
                // JS suite additionally challenges callback cancellation and both directions.
                native._write(data, "buffer", complete)
              catch {
                case error: Throwable =>
                  pending = None
                  operation.callback = None
                  wakeWriter()
                  throw error
              }
            }
            Some(F.delay {
              // Cancel only delivery. The native write still owns the serialization slot
              // until its callback settles; a subsequent writer waits in awaitIdle.
              pending.foreach { operation =>
                if (operation.callback.contains(cb)) operation.callback = None
              }
            })
          }
        })
      }
    }

    def endOfOutput: F[Unit] = writeLock.permit.use { _ =>
      F.uncancelable { poll =>
        poll(awaitIdle) *> F.delay {
          if (released) throw localFailure("output resource released")
          if (!outputEnded) {
            outputEnded = true
            val _ = native.end()
          }
          ()
        }
      }
    }

    def peer(transcript: Transcript[F]): F[RawPeer[F]] =
      RawPeer.create(read, write, endOfOutput, transcript)

    def release: F[Unit] = F.uncancelable { _ =>
      F.async_[Unit] { cb =>
        def remove(): Unit = {
          native.removeListener("readable", readable)
          native.removeListener("end", ended)
          native.removeListener("error", failed)
          native.removeListener("close", closed)
          ()
        }
        if (closeSeen) { remove(); cb(Right(())) }
        else {
          val done: js.Function0[Unit] = () => { remove(); cb(Right(())) }
          native.once("close", done)
        }
        if (!released) {
          released = true
          wakeRead()
          wakeWriter()
          pending.foreach { operation =>
            operation.callback.foreach(_(Left(localFailure("released during native write"))))
            operation.callback = None
          }
          pending = None
          // Retain the error listener until close: a previously queued native error must
          // not become an unhandled EventEmitter exception during resource cleanup.
          native.destroy()
          ()
        }
      }
    }
  }

  private def own[F[_]: Async](native: js.Dynamic): F[NativeSocket[F]] =
    (Semaphore[F](1), Semaphore[F](1)).mapN { (read, write) =>
      val socket = new NativeSocket[F](native, read, write)
      socket.install()
      socket
    }

  def connectNative[F[_]](
      address: SocketAddress[IpAddress]
  )(implicit F: Async[F]): Resource[F, NativeSocket[F]] =
    Resource
      .make(
        F.delay(
          js.Dynamic.newInstance(net.Socket)(
            js.Dynamic.literal(
              allowHalfOpen = true,
              highWaterMark = ReadSize,
            )
          )
        )
      )(socket => F.delay { socket.destroy(); () })
      .flatMap { native =>
        Resource.make(own[F](native))(_.release).evalTap { _ =>
          F.async[Unit] { cb =>
            F.delay {
              lazy val connected: js.Function0[Unit] = () => { clean(); cb(Right(())) }
              lazy val failed: js.Function1[js.Any, Unit] = error => {
                clean(); cb(Left(new ReadFailure(error)))
              }
              def clean(): Unit = {
                native.removeListener("connect", connected)
                native.removeListener("error", failed)
                ()
              }
              native.once("connect", connected)
              native.once("error", failed)
              try native.connect(address.port.value, address.host.toString)
              catch { case error: Throwable => clean(); throw error }
              Some(F.delay(clean()))
            }
          }
        }
      }

  final case class NativeListener[F[_]](
      address: SocketAddress[IpAddress],
      accept: Resource[F, NativeSocket[F]],
  )

  def bindNative[F[_]](implicit F: Async[F]): Resource[F, NativeListener[F]] = {
    // One connection per scripted case. Allocate its locks before listening so that the
    // connection callback can take ownership and install input/error listeners immediately.
    final class Ticket {
      var selected: Option[NativeSocket[F]] = None
      var callback: Option[Either[Throwable, NativeSocket[F]] => Unit] = None
    }
    final class State(read: Semaphore[F], write: Semaphore[F]) {
      val server: js.Dynamic = net.createServer(
        js.Dynamic.literal(
          allowHalfOpen = true,
          pauseOnConnect = true,
          highWaterMark = ReadSize,
        )
      )
      var owned: Option[NativeSocket[F]] = None
      var unclaimed: Option[NativeSocket[F]] = None
      var ticket: Option[Ticket] = None
      var failure: Option[Throwable] = None
      var closed = false
      val connection: js.Function1[js.Dynamic, Unit] = native =>
        if (closed || owned.nonEmpty) { native.destroy(); () }
        else {
          try {
            val socket = new NativeSocket[F](native, read, write)
            socket.install()
            owned = Some(socket)
            ticket match {
              case Some(waiting) =>
                waiting.selected = Some(socket)
                waiting.callback.foreach(_(Right(socket)))
                waiting.callback = None
                ticket = None
              case None => unclaimed = Some(socket)
            }
          } catch {
            case error: Throwable =>
              native.destroy()
              fail(error)
          }
        }
      def fail(cause: Throwable): Unit = {
        failure = Some(cause)
        ticket.foreach(_.callback.foreach(_(Left(cause))))
      }
      val failed: js.Function1[js.Any, Unit] = e => fail(new ReadFailure(e))
      server.on("connection", connection)
      server.on("error", failed)
    }
    Resource.eval((Semaphore[F](1), Semaphore[F](1)).tupled).flatMap { case (read, write) =>
      Resource
        .make(F.delay(new State(read, write)))(state =>
          F.delay {
            state.closed = true
            state.ticket.foreach { ticket =>
              ticket.callback.foreach(_(Left(localFailure("listener released"))))
              ticket.callback = None
            }
            state.unclaimed = None
          } *> F.defer(state.owned.traverse_(_.release)) *> F.async_[Unit] { cb =>
            val done: js.Function1[js.Any, Unit] = error => {
              state.server.removeListener("connection", state.connection)
              state.server.removeListener("error", state.failed)
              // Closing before listen completed is also a valid cancellation cleanup.
              if (
                error == null || js.isUndefined(error) || nativeCode(error, "code")
                  .contains("ERR_SERVER_NOT_RUNNING")
              ) cb(Right(()))
              else cb(Left(new ReadFailure(error)))
            }
            state.server.close(done)
            ()
          }
        )
        .evalTap { state =>
          F.async[Unit] { cb =>
            F.delay {
              lazy val listening: js.Function0[Unit] = () => { clean(); cb(Right(())) }
              lazy val failed: js.Function1[js.Any, Unit] = error => {
                clean(); cb(Left(new ReadFailure(error)))
              }
              def clean(): Unit = {
                state.server.removeListener("listening", listening)
                state.server.removeListener("error", failed)
                ()
              }
              state.server.once("listening", listening)
              state.server.once("error", failed)
              try state.server.listen(0, "127.0.0.1")
              catch { case error: Throwable => clean(); throw error }
              Some(F.delay(clean()))
            }
          }
        }
        .map { state =>
          val address = SocketAddress(
            ip"127.0.0.1",
            Port.fromInt(state.server.address().port.asInstanceOf[Int]).get,
          )
          val accept = Resource
            .make(F.delay(new Ticket))(ticket =>
              F.delay {
                if (state.ticket.contains(ticket)) state.ticket = None
                ticket.callback = None
              } *> F.defer(ticket.selected.traverse_(_.release))
            )
            .flatMap { ticket =>
              Resource.eval(F.async[NativeSocket[F]] { cb =>
                F.delay {
                  if (state.closed) cb(Left(localFailure("accept after listener release")))
                  else
                    state.failure match {
                      case Some(error) => cb(Left(error))
                      case None =>
                        state.unclaimed match {
                          case Some(socket) =>
                            state.unclaimed = None
                            ticket.selected = Some(socket)
                            cb(Right(socket))
                          case None =>
                            if (state.ticket.nonEmpty || state.owned.nonEmpty)
                              cb(Left(localFailure("reference listener accepts one connection")))
                            else { ticket.callback = Some(cb); state.ticket = Some(ticket) }
                        }
                    }
                  Some(F.delay {
                    if (state.ticket.contains(ticket)) state.ticket = None
                    ticket.callback = None
                  })
                }
              })
            }
          NativeListener(address, accept)
        }
    }
  }

  private var checked = false
  private def compatible[F[_]](implicit F: Async[F]): F[Unit] = F.defer {
    if (checked) F.unit
    else F.timeout(verifyCompatibility[F], 5.seconds) *> F.delay { checked = true }
  }

  /** Presence of _write is insufficient. Exercise the actual native callback/lifetime
    * semantics before permitting a protocol case. No version is silently skipped. This
    * canary is bounded, owns its sockets, and caches only a successfully completed probe.
    * Tests repeat it directly and add held-open injection/cancellation/budget controls.
    */
  def verifyCompatibility[F[_]](implicit F: Async[F]): F[Unit] =
    verifyCompatibility[F]((_: NativeSocket[F]) => F.unit)

  def verifyCompatibility[F[_]](
      prepare: NativeSocket[F] => F[Unit]
  )(implicit F: Async[F]): F[Unit] = {
    val payload = Chunk.array(Array.tabulate[Byte](225)(_.toByte))
    def pair: Resource[F, (NativeSocket[F], NativeSocket[F])] =
      bindNative[F].flatMap(listener =>
        (connectNative[F](listener.address), listener.accept).parTupled
      )
    def drain(socket: NativeSocket[F], accumulated: Vector[Byte] = Vector.empty): F[Vector[Byte]] =
      socket
        .read(17)
        .handleErrorWith { error =>
          if (RuntimeInfo.isConnectionReset(error)) F.pure(None) else F.raiseError(error)
        }
        .flatMap {
          case None => F.pure(accumulated)
          case Some(chunk) =>
            if (accumulated.size + chunk.size > payload.size)
              F.raiseError(localFailure("compatibility probe input exceeded bound"))
            else F.defer(drain(socket, accumulated ++ chunk.toVector))
        }
    def requireSame(actual: Vector[Byte]): F[Unit] =
      if (actual == payload.toVector) F.unit
      else F.raiseError(localFailure("private Node hook lost input during compatibility probe"))
    def awaitBuffered(socket: NativeSocket[F]): F[Unit] = F.defer {
      if (socket.native.readableLength.asInstanceOf[Int] == payload.size) F.unit
      else F.cede *> awaitBuffered(socket)
    }
    def reset(local: NativeSocket[F], remote: NativeSocket[F]): F[Unit] =
      F.async[Unit] { cb =>
        F.delay {
          if (js.typeOf(remote.native.resetAndDestroy) != "function")
            throw localFailure("Node resetAndDestroy is required for compatibility probe")
          val closed: js.Function0[Unit] = () => cb(Right(()))
          local.native.once("close", closed)
          remote.native.resetAndDestroy()
          Some(F.delay { local.native.removeListener("close", closed); () })
        }
      }
    Vector(false, true).traverse_ { accepted =>
      pair.use { case (client, server) =>
        val (local, remote) = if (accepted) (server, client) else (client, server)
        def replies(remaining: Int): F[Unit] =
          if (remaining == 0) F.unit
          else
            local.write(Chunk.singleton(0.toByte)).attempt.flatMap {
              case Left(error) if RuntimeInfo.isClosedWrite(error) => F.unit
              case Left(error) => F.raiseError(error)
              case Right(_) => F.defer(replies(remaining - 1))
            }
        prepare(local) *> remote.write(payload) *> remote.release *> replies(256) *> drain(local)
          .flatMap(requireSame)
      } *> pair.use { case (client, server) =>
        val (local, remote) = if (accepted) (server, client) else (client, server)
        for {
          _ <- prepare(local)
          _ <- remote.write(payload)
          _ <- awaitBuffered(local)
          _ <- reset(local, remote)
          result <- local.write(Chunk.singleton(0.toByte)).attempt
          _ <- result match {
            case Left(write: WriteFailure)
                if nativeCode(write.nativeError, "code").contains("ERR_SOCKET_CLOSED") &&
                  RuntimeInfo.isClosedWrite(write) && !RuntimeInfo.isConnectionReset(write) =>
              F.unit
            case _ =>
              F.raiseError[Unit](localFailure("private Node hook lost read-reset provenance"))
          }
          received <- drain(local)
          _ <- requireSame(received)
          terminal <- local.read(1).attempt
          _ <- terminal match {
            case Left(read: ReadFailure) if RuntimeInfo.isConnectionReset(read) => F.unit
            case _ =>
              F.raiseError[Unit](localFailure("compatibility probe lost independent read reset"))
          }
        } yield ()
      } *> pair.use { case (client, server) =>
        val (local, remote) = if (accepted) (server, client) else (client, server)
        local.endOfOutput *> remote.read(1).flatMap {
          case Some(_) => F.raiseError[Unit](localFailure("unexpected half-close probe input"))
          case None =>
            remote.write(payload) *> remote.endOfOutput *> drain(local).flatMap(requireSame)
        }
      }
    }
  }
}
