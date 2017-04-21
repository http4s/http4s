package org.http4s
package jawn

import _root_.jawn.{AsyncParser, Facade, ParseException}
import fs2.Stream
import jawnfs2._
import fs2.util.Catchable
import cats._
import cats.implicits._
import org.http4s.batteries.right

trait JawnInstances {
  def jawnDecoder[F[_], J](implicit C: Catchable[F], F: Applicative[F], facade: Facade[J]): EntityDecoder[F, J] =
    EntityDecoder.decodeBy(MediaType.`application/json`) { msg =>
      DecodeResult {
        msg.body.chunks
          .parseJson(AsyncParser.SingleValue)
          .map(right)
          .onError {
            case pe: ParseException =>
              Stream.emit(Left(MalformedMessageBodyFailure("Invalid JSON", Some(pe))))
            case e => Stream.fail(e)
          }
          .runLast
          .map(_.getOrElse(Left(MalformedMessageBodyFailure("Invalid JSON: empty body"))))
      }
    }
}
