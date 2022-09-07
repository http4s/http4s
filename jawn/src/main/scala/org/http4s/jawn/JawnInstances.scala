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

package org.http4s
package jawn

import cats.effect._
import cats.syntax.all._
import fs2.Stream
import org.typelevel.jawn.AsyncParser
import org.typelevel.jawn.Facade
import org.typelevel.jawn.ParseException
import org.typelevel.jawn.fs2._

trait JawnInstances {
  def jawnDecoder[F[_]: Concurrent, J: Facade]: EntityDecoder[F, J] =
    EntityDecoder.decodeBy(MediaType.application.json)(jawnDecoderImpl[F, J])

  protected def jawnParseExceptionMessage: ParseException => DecodeFailure =
    JawnInstances.defaultJawnParseExceptionMessage
  protected def jawnEmptyBodyMessage: DecodeFailure =
    JawnInstances.defaultJawnEmptyBodyMessage

  // some decoders may reuse it and avoid extra content negotiation
  private[http4s] def jawnDecoderImpl[F[_]: Concurrent, J: Facade](
      m: Media[F]
  ): DecodeResult[F, J] =
    DecodeResult {
      m.body.chunks
        .parseJson(AsyncParser.SingleValue)
        .map(Either.right)
        .handleErrorWith {
          case pe: ParseException =>
            Stream.emit(Left(jawnParseExceptionMessage(pe)))
          case e =>
            Stream.raiseError[F](e)
        }
        .compile
        .last
        .map(_.getOrElse(Left(jawnEmptyBodyMessage)))
    }
}

object JawnInstances {
  private[http4s] def defaultJawnParseExceptionMessage: ParseException => DecodeFailure =
    pe => MalformedMessageBodyFailure("Invalid JSON", Some(pe))

  private[http4s] def defaultJawnEmptyBodyMessage: DecodeFailure =
    MalformedMessageBodyFailure("Invalid JSON: empty body")
}
