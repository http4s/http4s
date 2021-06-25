/*
 * Copyright 2015 http4s.org
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
package circe

import cats.data.NonEmptyList
import cats.effect.Concurrent
import cats.syntax.all._
import fs2.Stream
import io.circe._
import cats.data.EitherT
import cats.data.OptionT

// TODO More efficient implementations?
private[circe] trait CirceInstancesPlatform { self: CirceInstances =>

  def emptyBodyMessage: DecodeFailure = CirceInstancesPlatform.defaultEmptyBodyMessage

  private def decodeJs[F[_]: Concurrent](msg: Media[F]): DecodeResult[F, Json] =
    EitherT(
      OptionT(msg.bodyText.compile.foldSemigroup)
        .fold[Either[DecodeFailure, Json]](Left(emptyBodyMessage)) { json =>
          parser.parse(json).leftMap { case ParsingFailure(msg, ex) =>
            MalformedMessageBodyFailure(msg, Some(ex))
          }
        }
    )

  def jsonDecoderIncremental[F[_]: Concurrent]: EntityDecoder[F, Json] =
    EntityDecoder.decodeBy(MediaType.application.json)(decodeJs[F])

  def jsonDecoderAdaptive[F[_]: Concurrent](
      cutoff: Long,
      r1: MediaRange,
      rs: MediaRange*): EntityDecoder[F, Json] =
    EntityDecoder.decodeBy(r1, rs: _*)(decodeJs[F])

  implicit def streamJsonArrayDecoder[F[_]: Concurrent]: EntityDecoder[F, Stream[F, Json]] =
    EntityDecoder.decodeBy(MediaType.application.json) { media =>
      DecodeResult.successT(Stream.eval(decodeJs[F](media).value).flatMap {
        case Right(json) => json.asArray.fold(Stream.emit(json))(Stream.emits)
        case Left(error) => Stream.raiseError(error)
      })
    }

  private[circe] def jsonDecoderByteBufferImpl[F[_]: Concurrent](
      m: Media[F]): DecodeResult[F, Json] =
    decodeJs(m)

}

private[circe] object CirceInstancesPlatform {
  val defaultEmptyBodyMessage = MalformedMessageBodyFailure("Invalid JSON: empty body")
}

abstract case class CirceInstancesBuilder private[circe] (
    defaultPrinter: Printer = Printer.noSpaces,
    jsonDecodeError: (Json, NonEmptyList[DecodingFailure]) => DecodeFailure =
      CirceInstances.defaultJsonDecodeError,
    circeParseExceptionMessage: ParsingFailure => DecodeFailure =
      CirceInstances.defaultCirceParseError,
    emptyBodyMessage: DecodeFailure = CirceInstancesPlatform.defaultEmptyBodyMessage
) { self =>
  def withPrinter(pp: Printer): CirceInstancesBuilder =
    this.copy(defaultPrinter = pp)

  def withJsonDecodeError(
      f: (Json, NonEmptyList[DecodingFailure]) => DecodeFailure): CirceInstancesBuilder =
    this.copy(jsonDecodeError = f)

  def withCirceParseExceptionMessage(f: ParsingFailure => DecodeFailure): CirceInstancesBuilder =
    this.copy(circeParseExceptionMessage = f)

  def withEmptyBodyMessage(df: DecodeFailure): CirceInstancesBuilder =
    this.copy(jawnEmptyBodyMessage = df)

  protected def copy(
      defaultPrinter: Printer = self.defaultPrinter,
      jsonDecodeError: (Json, NonEmptyList[DecodingFailure]) => DecodeFailure =
        self.jsonDecodeError,
      circeParseExceptionMessage: ParsingFailure => DecodeFailure = self.circeParseExceptionMessage,
      jawnEmptyBodyMessage: DecodeFailure = self.emptyBodyMessage
  ): CirceInstancesBuilder =
    new CirceInstancesBuilder(
      defaultPrinter,
      jsonDecodeError,
      circeParseExceptionMessage,
      emptyBodyMessage) {}

  def build: CirceInstances =
    new CirceInstances {
      override val defaultPrinter: Printer = self.defaultPrinter
      override val jsonDecodeError: (Json, NonEmptyList[DecodingFailure]) => DecodeFailure =
        self.jsonDecodeError
      override val circeParseExceptionMessage: ParsingFailure => DecodeFailure =
        self.circeParseExceptionMessage
      override val emptyBodyMessage: DecodeFailure = self.emptyBodyMessage
    }
}
