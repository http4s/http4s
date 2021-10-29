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
package booPickle
package instances

import boopickle.Default._
import boopickle.Pickler
import cats.effect.Sync
import fs2.Chunk
import org.http4s.EntityEncoder.chunkEncoder
import org.http4s._
import org.http4s.headers.`Content-Type`

import java.nio.ByteBuffer
import scala.util.Failure
import scala.util.Success

/** Generic factories for http4s encoders/decoders for boopickle
  * Note that the media type is set for application/octet-stream
  */
trait BooPickleInstances {
  private def booDecoderByteBuffer[F[_]: Sync, A](m: Media[F])(implicit
      pickler: Pickler[A]): DecodeResult[F, A] =
    EntityDecoder.collectBinary(m).subflatMap { chunk =>
      val bb = ByteBuffer.wrap(chunk.toArray)
      if (bb.hasRemaining)
        Unpickle[A](pickler).tryFromBytes(bb) match {
          case Success(bb) => Right(bb)
          case Failure(pf) => Left(MalformedMessageBodyFailure("Invalid binary body", Some(pf)))
        }
      else
        Left(MalformedMessageBodyFailure("Invalid binary: empty body", None))
    }

  /** Create an `EntityDecoder` for `A` given a `Pickler[A]`
    */
  def booOf[F[_]: Sync, A: Pickler]: EntityDecoder[F, A] =
    EntityDecoder.decodeBy(MediaType.application.`octet-stream`)(booDecoderByteBuffer[F, A])

  /** Create an `EntityEncoder` for `A` given a `Pickler[A]`
    */
  def booEncoderOf[F[_], A: Pickler]: EntityEncoder[F, A] =
    chunkEncoder[F]
      .contramap[A] { v =>
        Chunk.ByteBuffer(Pickle.intoBytes(v))
      }
      .withContentType(`Content-Type`(MediaType.application.`octet-stream`))
}
