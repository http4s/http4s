/*
 * Copyright 2018 http4s.org
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

package org.http4s
package jetty
package client

import cats.effect._
import cats.effect.concurrent.Semaphore
import cats.effect.implicits._
import cats.syntax.all._
import fs2._
import org.eclipse.jetty.client.util.DeferredContentProvider
import org.eclipse.jetty.util.{Callback => JettyCallback}
import org.http4s.internal.loggingAsyncCallback
import org.log4s.getLogger

private[jetty] final case class StreamRequestContentProvider[F[_]](s: Semaphore[F])(implicit
    F: Effect[F]
) extends DeferredContentProvider {
  import StreamRequestContentProvider.logger

  def write(req: Request[F]): F[Unit] =
    req.body.chunks
      .through(pipe)
      .compile
      .drain
      .onError { case t => F.delay(logger.error(t)("Unable to write to Jetty sink")) }

  private val pipe: Pipe[F, Chunk[Byte], Unit] =
    _.evalMap { c =>
      write(c)
        .ensure(new Exception("something terrible has happened"))(res => res)
        .map(_ => ())
    }

  private def write(chunk: Chunk[Byte]): F[Boolean] =
    s.acquire
      .map(_ => super.offer(chunk.toByteBuffer, callback))

  private val callback: JettyCallback = new JettyCallback {
    override def succeeded(): Unit =
      s.release.runAsync(loggingAsyncCallback(logger)).unsafeRunSync()
  }
}

private[jetty] object StreamRequestContentProvider {
  private val logger = getLogger

  def apply[F[_]]()(implicit F: ConcurrentEffect[F]): F[StreamRequestContentProvider[F]] =
    Semaphore[F](1).map(StreamRequestContentProvider(_))
}
