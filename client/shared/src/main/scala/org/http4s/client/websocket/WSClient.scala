/*
 * Copyright 2014 http4s.org
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

package org.http4s.client.websocket

import cats._
import cats.data.Chain
import cats.data.OptionT
import cats.effect._
import cats.effect.kernel.Deferred
import cats.effect.kernel.DeferredSource
import cats.implicits._
import fs2.Pipe
import fs2.Stream
import org.http4s.Headers
import org.http4s.Method
import org.http4s.Uri
import org.http4s.internal.reduceComparisons
import scodec.bits.ByteVector

/** A websocket request.
  *
  * @param uri
  *   The URI.
  * @param headers
  *   The headers to send. Put your `Sec-Websocket-Protocol` headers here if needed. Some websocket
  *   clients reject other WS-specific headers.
  * @param method
  *   The method of the intial HTTP request. Ignored by some clients.
  */
sealed abstract class WSRequest {
  def uri: Uri
  def headers: Headers
  def method: Method

  def withUri(uri: Uri): WSRequest
  def withHeaders(headers: Headers): WSRequest
  def withMethod(method: Method): WSRequest
}

object WSRequest {
  def apply(uri: Uri): WSRequest = apply(uri, Headers.empty, Method.GET)

  def apply(uri: Uri, headers: Headers, method: Method): WSRequest =
    WSRequestImpl(uri, headers, method)

  private[this] final case class WSRequestImpl(
      override val uri: Uri,
      override val headers: Headers,
      override val method: Method,
  ) extends WSRequest {
    def withUri(uri: Uri): WSRequestImpl = copy(uri = uri)
    def withHeaders(headers: Headers): WSRequestImpl = copy(headers = headers)
    def withMethod(method: Method): WSRequestImpl = copy(method = method)
  }

  implicit val catsHashAndOrderForWSRequest: Hash[WSRequest] with Order[WSRequest] =
    new Hash[WSRequest] with Order[WSRequest] {
      override def hash(x: WSRequest): Int = x.hashCode

      override def compare(x: WSRequest, y: WSRequest): Int =
        reduceComparisons(
          x.headers.compare(y.headers),
          Eval.later(x.method.compare(y.method)),
          Eval.later(x.uri.compare(y.uri)),
        )
    }

  implicit val catsShowForWSRequest: Show[WSRequest] =
    Show.fromToString

  implicit def stdLibOrdering: Ordering[WSRequest] =
    catsHashAndOrderForWSRequest.toOrdering
}

sealed trait WSFrame extends Product with Serializable

sealed trait WSControlFrame extends WSFrame

sealed trait WSDataFrame extends WSFrame

object WSFrame {
  final case class Close(statusCode: Int, reason: String) extends WSControlFrame
  final case class Ping(data: ByteVector) extends WSControlFrame
  final case class Pong(data: ByteVector) extends WSControlFrame
  final case class Text(data: String, last: Boolean = true) extends WSDataFrame
  final case class Binary(data: ByteVector, last: Boolean = true) extends WSDataFrame
}

trait WSConnection[F[_]] { outer =>

  /** Send a single websocket frame. The sending side of this connection has to be open. */
  def send(wsf: WSFrame): F[Unit]

  /** Send multiple websocket frames. Equivalent to multiple `send` calls, but at least as fast. */
  def sendMany[G[_]: Foldable, A <: WSFrame](wsfs: G[A]): F[Unit]

  /** A `Pipe` which sends websocket frames and emits a `()` for each chunk sent. */
  def sendPipe: Pipe[F, WSFrame, Unit] = _.chunks.evalMap(sendMany(_))

  /** Wait for a single websocket frame to be received. Returns `None` if the receiving side is
    * closed.
    */
  def receive: F[Option[WSFrame]]

  /** A stream of the incoming websocket frames. */
  def receiveStream: Stream[F, WSFrame] = Stream.repeatEval(receive).unNoneTerminate

  /** The negotiated subprotocol, if any. */
  def subprotocol: Option[String]

  def mapK[G[_]](fk: F ~> G): WSConnection[G] = new WSConnection[G] {
    def send(wsf: WSFrame): G[Unit] = fk(outer.send(wsf))
    def sendMany[H[_]: Foldable, A <: WSFrame](wsfs: H[A]): G[Unit] = fk(outer.sendMany(wsfs))
    def receive: G[Option[WSFrame]] = fk(outer.receive)
    def subprotocol: Option[String] = outer.subprotocol
  }
}

trait WSConnectionHighLevel[F[_]] { outer =>

  /** Send a single websocket text frame. The sending side of this connection has to be open. */
  def sendText(text: String): F[Unit] = send(WSFrame.Text(text))

  /** Send a single websocket binary frame. The sending side of this connection has to be open. */
  def sendBinary(bytes: ByteVector): F[Unit] = send(WSFrame.Binary(bytes))

  /** Send a single websocket frame. The sending side of this connection has to be open. */
  def send(wsf: WSDataFrame): F[Unit]

  /** Send multiple websocket frames. Equivalent to multiple `send` calls, but at least as fast. */
  def sendMany[G[_]: Foldable, A <: WSDataFrame](wsfs: G[A]): F[Unit]

  /** A `Pipe` which sends websocket frames and emits a `()` for each chunk sent. */
  def sendPipe: Pipe[F, WSDataFrame, Unit] = _.chunks.evalMap(sendMany(_))

  /** Wait for a websocket frame to be received. Returns `None` if the receiving side is closed.
    * Fragmentation is handled automatically, the `last` attribute can be ignored.
    */
  def receive: F[Option[WSDataFrame]]

  /** A stream of the incoming websocket frames. */
  def receiveStream: Stream[F, WSDataFrame] = Stream.repeatEval(receive).unNoneTerminate

  /** The negotiated subprotocol, if any. */
  def subprotocol: Option[String]

  /** The close frame, if available. */
  def closeFrame: DeferredSource[F, WSFrame.Close]

  def mapK[G[_]](fk: F ~> G): WSConnectionHighLevel[G] =
    new WSConnectionHighLevel[G] {
      def send(wsf: WSDataFrame): G[Unit] = fk(outer.send(wsf))
      def sendMany[H[_]: Foldable, A <: WSDataFrame](wsfs: H[A]): G[Unit] = fk(outer.sendMany(wsfs))
      def receive: G[Option[WSDataFrame]] = fk(outer.receive)
      def subprotocol: Option[String] = outer.subprotocol
      def closeFrame: DeferredSource[G, WSFrame.Close] = new DeferredSource[G, WSFrame.Close] {
        def get = fk(outer.closeFrame.get)
        def tryGet = fk(outer.closeFrame.tryGet)
      }
    }
}

/** A websocket client capable of establishing [[WSClientHighLevel#connectHighLevel "high level" connections]].
  * @see [[WSClient]] for a client also capable of "low-level" connections
  */
trait WSClientHighLevel[F[_]] { outer =>

  /** Establish a "high level" websocket connection. You only get to handle Text and Binary frames.
    * Pongs will be replied automatically. Received frames are grouped by the `last` attribute. The
    * connection will be closed automatically.
    */
  def connectHighLevel(request: WSRequest): Resource[F, WSConnectionHighLevel[F]]

  def mapK[G[_]](
      fk: F ~> G
  )(implicit F: MonadCancel[F, _], G: MonadCancel[G, _]): WSClientHighLevel[G] =
    new WSClientHighLevel[G] {
      def connectHighLevel(request: WSRequest): Resource[G, WSConnectionHighLevel[G]] =
        outer.connectHighLevel(request).map(_.mapK(fk)).mapK(fk)
    }
}

trait WSClient[F[_]] extends WSClientHighLevel[F] { outer =>

  /** Establish a websocket connection. It will be closed automatically if necessary. */
  def connect(request: WSRequest): Resource[F, WSConnection[F]]

  override def mapK[G[_]](
      fk: F ~> G
  )(implicit F: MonadCancel[F, _], G: MonadCancel[G, _]): WSClient[G] =
    new WSClient[G] {
      def connectHighLevel(request: WSRequest): Resource[G, WSConnectionHighLevel[G]] =
        outer.connectHighLevel(request).map(_.mapK(fk)).mapK(fk)

      def connect(request: WSRequest): Resource[G, WSConnection[G]] =
        outer.connect(request).map(_.mapK(fk)).mapK(fk)
    }
}

object WSClient {
  def apply[F[_]](
      respondToPings: Boolean
  )(f: WSRequest => Resource[F, WSConnection[F]])(implicit F: Concurrent[F]): WSClient[F] =
    new WSClient[F] {
      override def connect(request: WSRequest) = f(request)
      override def connectHighLevel(request: WSRequest) =
        for {
          recvCloseFrame <- Resource.eval(Deferred[F, WSFrame.Close])
          conn <- f(request)
        } yield new WSConnectionHighLevel[F] {
          override def send(wsf: WSDataFrame) = conn.send(wsf)
          override def sendMany[G[_]: Foldable, A <: WSDataFrame](wsfs: G[A]): F[Unit] =
            conn.sendMany(wsfs)
          override def receive: F[Option[WSDataFrame]] = {
            def receiveDataFrame: OptionT[F, WSDataFrame] =
              OptionT(conn.receive).flatMap { wsf =>
                OptionT.liftF(wsf match {
                  case WSFrame.Ping(data) if respondToPings => conn.send(WSFrame.Pong(data))
                  case wsf: WSFrame.Close =>
                    recvCloseFrame.complete(wsf) *> conn.send(wsf)
                  case _ => F.unit
                }) >> (wsf match {
                  case wsdf: WSDataFrame => OptionT.pure[F](wsdf)
                  case _ => receiveDataFrame
                })
              }
            def defrag(text: Chain[String], binary: ByteVector): OptionT[F, WSDataFrame] =
              receiveDataFrame.flatMap {
                case WSFrame.Text(t, finalFrame) =>
                  val nextText = text :+ t
                  if (finalFrame) {
                    val sb = new StringBuilder(nextText.foldMap(_.length))
                    nextText.iterator.foreach(sb ++= _)
                    OptionT.pure[F](WSFrame.Text(sb.mkString))
                  } else
                    defrag(nextText, binary)
                case WSFrame.Binary(b, finalFrame) =>
                  val nextBinary = binary ++ b
                  if (finalFrame)
                    OptionT.pure[F](WSFrame.Binary(nextBinary))
                  else
                    defrag(text, nextBinary)
              }
            defrag(Chain.empty, ByteVector.empty).value
          }
          override def subprotocol: Option[String] = conn.subprotocol
          override def closeFrame: DeferredSource[F, WSFrame.Close] = recvCloseFrame
        }
    }
}
