package org.http4s

import cats.data._
import cats.implicits._
import org.http4s.internal.parboiled2._
import org.http4s.internal.parboiled2.Parser.DeliveryScheme
import org.http4s.internal.parboiled2.support._

package object parser {
  private[this] val errorFormatter = new ErrorFormatter

  implicit def parseResultDeliveryScheme[L <: HList, Out](implicit unpack: Unpack.Aux[L, Out]) =
    // scalastyle:off public.methods.have.type
    new DeliveryScheme[L] {
      type Result = ParseResult[Out]

      def success(result: L) =
        Either.right(unpack(result))

      def parseError(error: ParseError) =
        Either.left(ParseFailure("", errorFormatter.formatExpectedAsString(error)))

      def failure(error: Throwable) =
        Either.left(ParseFailure("Exception during parsing.", error.getMessage))
    }
    // scalastyle:on public.methods.have.type  
}
