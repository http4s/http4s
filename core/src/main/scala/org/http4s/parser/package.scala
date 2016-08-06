package org.http4s

import org.http4s.batteries._
import org.parboiled2._
import org.parboiled2.Parser.DeliveryScheme
import org.parboiled2.support.Unpack
import shapeless._

package object parser {
  private[this] val errorFormatter = new ErrorFormatter

  implicit def parseResultDeliveryScheme[L <: HList, Out](implicit unpack: Unpack.Aux[L, Out]) =
    // scalastyle:off public.methods.have.type
    new DeliveryScheme[L] {
      type Result = ParseResult[Out]
      def success(result: L) = right(unpack(result))
      def parseError(error: ParseError) = left(ParseFailure("", errorFormatter.formatExpectedAsString(error)))
      def failure(error: Throwable) = left(ParseFailure("Exception during parsing.", error.getMessage))
    }
    // scalastyle:on public.methods.have.type  
}
