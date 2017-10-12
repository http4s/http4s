package org.http4s

import cats._
import cats.implicits._
import org.scalacheck.{Arbitrary, Properties}
import org.scalacheck.Prop._

/**
  * Instances of [[QueryParamDecoder]] and [[QueryParamEncoder]]
  * must satisfy the following properties
  */
object QueryParamCodecLaws {

  def apply[T: Arbitrary: Eq: QueryParamDecoder: QueryParamEncoder] =
    new Properties("QueryParamCodec") {

      property("decode . encode == successNel") = forAll { value: T =>
        (QueryParamDecoder[T].decode _)
          .compose(QueryParamEncoder[T].encode)(value) === value.validNel
      }

    }
}
