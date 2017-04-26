package org.http4s.parser

import scalaz._

import org.http4s.ParseFailure
import org.http4s.internal.parboiled2.{ ErrorFormatter, ParseError }
import org.http4s.internal.parboiled2.Parser.DeliveryScheme
import org.http4s.internal.parboiled2.support.Unpack
import org.http4s.internal.parboiled2.support.HList

private[http4s] object ScalazDeliverySchemes {
  private val errorFormattter = new ErrorFormatter()

  // scalastyle:off public.methods.have.type
  implicit def Disjunction[L <: HList, Out](implicit unpack: Unpack.Aux[L, Out]) =
    new DeliveryScheme[L] {
      type Result = ParseError \/ Out
      def success(result: L) = \/-(unpack(result))
      def parseError(error: ParseError) = -\/(error)
      def failure(error: Throwable) = throw error
    }
  // scalastyle:on public.methods.have.type
}
