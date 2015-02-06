package org.http4s.parser

import scalaz._

import org.http4s.ParseFailure
import org.parboiled2.ParseError
import org.parboiled2.Parser.DeliveryScheme
import org.parboiled2.support.Unpack
import shapeless.HList

private[http4s] object ScalazDeliverySchemes {
  implicit def Disjunction[L <: HList, Out](implicit unpack: Unpack.Aux[L, Out]) =
    new DeliveryScheme[L] {
      type Result = ParseFailure \/ Out
      def success(result: L) = \/-(unpack(result))
      def parseError(error: ParseError) = -\/(ParseFailure("", error.formatExpectedAsString))
      def failure(error: Throwable) = -\/(ParseFailure("Exception during parsing.", error.getMessage))
    }
}
