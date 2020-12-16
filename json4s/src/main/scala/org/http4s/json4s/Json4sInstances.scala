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
package json4s

import cats.effect.{MonadThrow, Sync}
import cats.syntax.all._
import fs2.Stream
import org.http4s.headers.`Content-Type`
import org.json4s._
import org.json4s.JsonAST.JValue
import org.typelevel.jawn.support.json4s.Parser

object CustomParser extends Parser(useBigDecimalForDouble = true, useBigIntForLong = true)

trait Json4sInstances[J] {
  implicit def jsonDecoder[F[_]](implicit
      C: Stream.Compiler[F, F],
      A: MonadThrow[F]): EntityDecoder[F, JValue] =
    jawn.jawnDecoder(CustomParser.facade, C, A)

  def jsonOf[F[_], A](implicit
      reader: Reader[A],
      C: Stream.Compiler[F, F],
      A: MonadThrow[F]): EntityDecoder[F, A] =
    jsonDecoder.flatMapR { json =>
      DecodeResult(
        A.catchNonFatal(reader.read(json))
          .map[Either[DecodeFailure, A]](Right(_))
          .recover { case e: MappingException =>
            Left(InvalidMessageBodyFailure("Could not map JSON", Some(e)))
          })
    }

  /** Uses formats to extract a value from JSON.
    *
    * Editorial: This is heavily dependent on reflection. This is more idiomatic json4s, but less
    * idiomatic http4s, than [[jsonOf]].
    */
  def jsonExtract[F[_], A](implicit
      C: Stream.Compiler[F, F],
      A: MonadThrow[F],
      formats: Formats,
      manifest: Manifest[A]): EntityDecoder[F, A] =
    jsonDecoder.flatMapR { json =>
      DecodeResult(
        A.catchNonFatal[Either[DecodeFailure, A]](Right(json.extract[A]))
          .handleError(e => Left(InvalidMessageBodyFailure("Could not extract JSON", Some(e)))))
    }

  protected def jsonMethods: JsonMethods[J]

  implicit def jsonEncoder[F[_], A <: JValue]: EntityEncoder[F, A] =
    EntityEncoder
      .stringEncoder(Charset.`UTF-8`)
      .contramap[A] { json =>
        // TODO naive implementation materializes to a String.
        // Look into replacing after https://github.com/non/jawn/issues/6#issuecomment-65018736
        jsonMethods.compact(jsonMethods.render(json))
      }
      .withContentType(`Content-Type`(MediaType.application.json))

  def jsonEncoderOf[F[_], A](implicit writer: Writer[A]): EntityEncoder[F, A] =
    jsonEncoder[F, JValue].contramap[A](writer.write)

  implicit val uriWriter: JsonFormat[Uri] =
    new JsonFormat[Uri] {
      def read(json: JValue): Uri =
        json match {
          case JString(s) =>
            Uri
              .fromString(s)
              .fold(
                _ => throw new MappingException(s"Can't convert $json to Uri."),
                identity
              )
          case _ =>
            throw new MappingException(s"Can't convert $json to Uri.")
        }

      def write(uri: Uri): JValue =
        JString(uri.toString)
    }

  implicit class MessageSyntax[F[_]: Sync](self: Message[F]) {
    def decodeJson[A](implicit decoder: Reader[A]): F[A] =
      self.as(implicitly, jsonOf[F, A])
  }
}
