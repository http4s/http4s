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
import cats.syntax.either._
import fs2.Stream
import io.circe._
import io.circe.jawn._
import org.http4s.jawn.JawnInstances
import org.typelevel.jawn.ParseException
import org.typelevel.jawn.fs2.unwrapJsonArray
import java.nio.ByteBuffer

private[circe] trait CirceInstancesPlatform extends JawnInstances { self: CirceInstances =>
  protected val circeSupportParser =
    new CirceSupportParser(maxValueSize = None, allowDuplicateKeys = false)

  import circeSupportParser.facade

  def jsonDecoderIncremental[F[_]: Concurrent]: EntityDecoder[F, Json] =
    this.jawnDecoder[F, Json]

  def jsonDecoderAdaptive[F[_]: Concurrent](
      cutoff: Long,
      r1: MediaRange,
      rs: MediaRange*): EntityDecoder[F, Json] =
    EntityDecoder.decodeBy(r1, rs: _*) { msg =>
      msg.contentLength match {
        case Some(contentLength) if contentLength < cutoff =>
          jsonDecoderByteBufferImpl[F](msg)
        case _ => this.jawnDecoderImpl[F, Json](msg)
      }
    }

  implicit def streamJsonArrayDecoder[F[_]: Concurrent]: EntityDecoder[F, Stream[F, Json]] =
    EntityDecoder.decodeBy(MediaType.application.json) { media =>
      DecodeResult.successT(media.body.chunks.through(unwrapJsonArray))
    }

  private[circe] def jsonDecoderByteBufferImpl[F[_]: Concurrent](
      m: Media[F]): DecodeResult[F, Json] =
    EntityDecoder.collectBinary(m).subflatMap { chunk =>
      val bb = ByteBuffer.wrap(chunk.toArray)
      if (bb.hasRemaining)
        circeSupportParser
          .parseFromByteBuffer(bb)
          .toEither
          .leftMap(e => circeParseExceptionMessage(ParsingFailure(e.getMessage(), e)))
      else
        Left(jawnEmptyBodyMessage)
    }

}

abstract case class CirceInstancesBuilder private[circe] (
    defaultPrinter: Printer = Printer.noSpaces,
    jsonDecodeError: (Json, NonEmptyList[DecodingFailure]) => DecodeFailure =
      CirceInstances.defaultJsonDecodeError,
    circeParseExceptionMessage: ParsingFailure => DecodeFailure =
      CirceInstances.defaultCirceParseError,
    jawnParseExceptionMessage: ParseException => DecodeFailure =
      JawnInstances.defaultJawnParseExceptionMessage,
    jawnEmptyBodyMessage: DecodeFailure = JawnInstances.defaultJawnEmptyBodyMessage,
    circeSupportParser: CirceSupportParser =
      new CirceSupportParser(maxValueSize = None, allowDuplicateKeys = false)
) { self =>
  def withPrinter(pp: Printer): CirceInstancesBuilder =
    this.copy(defaultPrinter = pp)

  def withJsonDecodeError(
      f: (Json, NonEmptyList[DecodingFailure]) => DecodeFailure): CirceInstancesBuilder =
    this.copy(jsonDecodeError = f)

  def withJawnParseExceptionMessage(f: ParseException => DecodeFailure): CirceInstancesBuilder =
    this.copy(jawnParseExceptionMessage = f)
  def withCirceParseExceptionMessage(f: ParsingFailure => DecodeFailure): CirceInstancesBuilder =
    this.copy(circeParseExceptionMessage = f)

  def withEmptyBodyMessage(df: DecodeFailure): CirceInstancesBuilder =
    this.copy(jawnEmptyBodyMessage = df)

  def withCirceSupportParser(csp: CirceSupportParser): CirceInstancesBuilder =
    this.copy(circeSupportParser = csp)

  protected def copy(
      defaultPrinter: Printer = self.defaultPrinter,
      jsonDecodeError: (Json, NonEmptyList[DecodingFailure]) => DecodeFailure =
        self.jsonDecodeError,
      circeParseExceptionMessage: ParsingFailure => DecodeFailure = self.circeParseExceptionMessage,
      jawnParseExceptionMessage: ParseException => DecodeFailure = self.jawnParseExceptionMessage,
      jawnEmptyBodyMessage: DecodeFailure = self.jawnEmptyBodyMessage,
      circeSupportParser: CirceSupportParser = self.circeSupportParser
  ): CirceInstancesBuilder =
    new CirceInstancesBuilder(
      defaultPrinter,
      jsonDecodeError,
      circeParseExceptionMessage,
      jawnParseExceptionMessage,
      jawnEmptyBodyMessage,
      circeSupportParser) {}

  def build: CirceInstances =
    new CirceInstances {
      override val circeSupportParser: CirceSupportParser = self.circeSupportParser
      override val defaultPrinter: Printer = self.defaultPrinter
      override val jsonDecodeError: (Json, NonEmptyList[DecodingFailure]) => DecodeFailure =
        self.jsonDecodeError
      override val circeParseExceptionMessage: ParsingFailure => DecodeFailure =
        self.circeParseExceptionMessage
      override val jawnParseExceptionMessage: ParseException => DecodeFailure =
        self.jawnParseExceptionMessage
      override val jawnEmptyBodyMessage: DecodeFailure = self.jawnEmptyBodyMessage
    }
}
