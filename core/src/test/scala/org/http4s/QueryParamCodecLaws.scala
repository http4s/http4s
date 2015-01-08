package org.http4s

import org.scalacheck.Prop._
import org.scalacheck.{Arbitrary, Properties}

import scalaz.Equal
import scalaz.syntax.validation._

/**
 * Instances of [[QueryParamDecoder]] and [[QueryParamEncoder]]
 * must satisfy the following properties
 */
object QueryParamCodecLaws {

  def apply[T: Arbitrary: Equal: QueryParamDecoder: QueryParamEncoder] = new Properties("QueryParamCodec") {

    property("decode . encode == successNel") = forAll { value: T =>
      val r = QueryParamEncoder[T].encode(value).map(QueryParamDecoder[T].decode)
      r.length == 1 && r.head === value.successNel
    }

  }
}
