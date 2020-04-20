package org.http4s
package jawn

import cats.effect._
import cats.implicits._
import fs2.Stream
import org.typelevel.jawn.{AsyncParser, Facade, ParseException}
import jawnfs2._
import org.http4s.implicits._

trait JawnInstances {
  def jawnDecoder[F[_]: Sync, J: Facade]: EntityDecoder[F, J] =
    new EntityDecoder.DecodeByMediaRange[F, J](MediaType.application.json){
      def decodeForall[M[_[_]]: Media](m: M[F]): org.http4s.DecodeResult[F,J] = 
        jawnDecoderImpl(m)
    }

  protected def jawnParseExceptionMessage: ParseException => DecodeFailure =
    JawnInstances.defaultJawnParseExceptionMessage
  protected def jawnEmptyBodyMessage: DecodeFailure =
    JawnInstances.defaultJawnEmptyBodyMessage

  // some decoders may reuse it and avoid extra content negotiation
  private[http4s] def jawnDecoderImpl[M[_[_]]: Media, F[_]: Sync, J: Facade](m: M[F]): DecodeResult[F, J] =
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
