/*
 * Copyright 2021 http4s.org
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
package dom

import cats.effect.kernel.Async
import org.scalajs.dom.File
import org.scalajs.dom.experimental.ReadableStream
import fs2.Stream

import scala.scalajs.js
import scala.scalajs.js.typedarray.Uint8Array

object DomEntityEncoder {

  implicit def fileEncoder[F[_]](implicit F: Async[F]): EntityEncoder[F, File] =
    EntityEncoder.entityBodyEncoder.contramap { file =>
      Stream
        .bracketCase {
          // Unfortunately stream() method is missing from the File facade
          F.delay(file.asInstanceOf[js.Dynamic].stream().asInstanceOf[ReadableStream[Uint8Array]])
        } { case (rs, exitCase) => closeReadableStream(rs, exitCase) }
        .flatMap(fromReadableStream[F])
    }

  implicit def readableStreamEncoder[F[_]: Async]: EntityEncoder[F, ReadableStream[Uint8Array]] =
    EntityEncoder.entityBodyEncoder.contramap { rs =>
      fromReadableStream(rs)
    }

}
