package org.http4s

import scalaz.std.string._
import scalaz.std.anyVal._

class QueryParamCodecSpec extends Http4sSpec {

  checkAll("Boolean QueryParamCodec", QueryParamCodecLaws[Boolean])
  checkAll("Double QueryParamCodec" , QueryParamCodecLaws[Double])
  checkAll("Float QueryParamCodec"  , QueryParamCodecLaws[Float])
  checkAll("Short QueryParamCodec"  , QueryParamCodecLaws[Short])
  checkAll("Int QueryParamCodec"    , QueryParamCodecLaws[Int])
  checkAll("Long QueryParamCodec"   , QueryParamCodecLaws[Long])
  checkAll("String QueryParamCodec" , QueryParamCodecLaws[String])
}
