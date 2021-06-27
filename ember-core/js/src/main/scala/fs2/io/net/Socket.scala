/*
 * Copyright 2019 http4s.org
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

package fs2
package io
package net

import com.comcast.ip4s.{IpAddress, SocketAddress}

/** Provides the ability to read/write from a TCP socket in the effect `F`.
  */
trait Socket[F[_]] {

  /** Reads up to `maxBytes` from the peer.
    *
    * Returns `None` if the "end of stream" is reached, indicating there will be no more bytes sent.
    */
  def read(maxBytes: Int): F[Option[Chunk[Byte]]]

  /** Reads exactly `numBytes` from the peer in a single chunk.
    *
    * Returns a chunk with size < `numBytes` upon reaching the end of the stream.
    */
  def readN(numBytes: Int): F[Chunk[Byte]]

  /** Reads bytes from the socket as a stream. */
  def reads: Stream[F, Byte]

  /** Indicates that this channel will not read more data. Causes `End-Of-Stream` be signalled to `available`. */
  def endOfInput: F[Unit]

  /** Indicates to peer, we are done writing. * */
  def endOfOutput: F[Unit]

  def isOpen: F[Boolean]

  /** Asks for the remote address of the peer. */
  def remoteAddress: F[SocketAddress[IpAddress]]

  /** Asks for the local address of the socket. */
  def localAddress: F[SocketAddress[IpAddress]]

  /** Writes `bytes` to the peer.
    *
    * Completes when the bytes are written to the socket.
    */
  def write(bytes: Chunk[Byte]): F[Unit]

  /** Writes the supplied stream of bytes to this socket via `write` semantics.
    */
  def writes: Pipe[F, Byte, INothing]
}
