/*
 * Copyright 2013 http4s.org
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
package multipart

import fs2.Compiler.Target
import cats.syntax.all._
import fs2.Pipe

private[http4s] object MultipartDecoder extends MultipartDecoderPlatform {
  def decoder[F[_]: Target]: EntityDecoder[F, Multipart[F]] =
    makeDecoder(MultipartParser.parseToPartsStream[F](_))

  private[multipart] def makeDecoder[F[_]: Target](
      impl: Boundary => Pipe[F, Byte, Part[F]]
  ): EntityDecoder[F, Multipart[F]] =
    EntityDecoder.decodeBy(MediaRange.`multipart/*`) { msg =>
      msg.contentType.flatMap(_.mediaType.extensions.get("boundary")) match {
        case Some(boundary) =>
          DecodeResult {
            msg.body
              .through(impl(Boundary(boundary)))
              .compile
              .toVector
              .map[Either[DecodeFailure, Multipart[F]]](parts =>
                Right(Multipart(parts, Boundary(boundary))))
              .handleError {
                case e: InvalidMessageBodyFailure => Left(e)
                case e => Left(InvalidMessageBodyFailure("Invalid multipart body", Some(e)))
              }
          }
        case None =>
          DecodeResult.failureT(
            InvalidMessageBodyFailure("Missing boundary extension to Content-Type"))
      }
    }
}
