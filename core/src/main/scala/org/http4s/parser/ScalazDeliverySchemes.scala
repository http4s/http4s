package org.http4s.parser

import scalaz._

import org.http4s.ParseFailure
import org.parboiled2.{ ErrorFormatter, ParseError }
import org.parboiled2.Parser.DeliveryScheme
import org.parboiled2.support.Unpack
import shapeless.HList

private[http4s] object ScalazDeliverySchemes {
  private val errorFormattter = new ErrorFormatter()

  // scalastyle:off public.methods.have.type
  implicit def Disjunction[L <: HList, Out](implicit unpack: Unpack.Aux[L, Out]) =
    new DeliveryScheme[L] {
      type Result = ParseFailure \/ Out
      def success(result: L) = \/-(unpack(result))
      def parseError(error: ParseError) = -\/(ParseFailure("", errorFormattter.formatExpectedAsString(error)))
      def failure(error: Throwable) = -\/(ParseFailure("Exception during parsing.", error.getMessage))
    }
  // scalastyle:on public.methods.have.type
}
