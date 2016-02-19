package org.http4s.util.encoding

import org.http4s.Http4sSpec
import org.specs2.{ScalaCheck, Specification}

class UriCodingSpec extends Http4sSpec with ScalaCheck {
  import UriCodingUtils._
//  def is =
//    s2"""
//       ${ U.encodePath(Seq("", "é", "")).encoded must_== "/%C3%A9/" }
//      """
  //       plusIsSpace flag specifies how to treat pluses
  //        treats + as allowed when the plusAsSpace flag is ommitted or false ${
  //          UriCodingUtils.encodeQueryParam("+").encoded must_== "+"
  //        }

//  "verifyAuthority . encodeAuthority is right" in prop {
//    s: String => verifyAuthority(encodeAu)
//  }

  "verifyUserInfo . encodeUserInfo is right" in prop {
    s: String => verifyUserInfo(encodeUserInfo(s).encoded) must beSuccessfulTry
  }

  "verifyRegName . encodeRegName is right" in prop {
    s: String => verifyRegName(encodeRegName(s).encoded) must beSuccessfulTry
  }

  "verifyFragment . encodeFragment is right" in prop {
    s: String => verifyFragment(encodeFragment(s).encoded) must beSuccessfulTry
  }

  "verifyPath . encodePath is right" in prop {
    p: Seq[String] => verifyPath(encodePath(p).encoded) must beSuccessfulTry
  }

  "verifyQuery . encodeQueryMapOrdered is right" in prop {
    q: Map[String, Seq[String]] => verifyQuery(encodeQueryMap(q).encoded) must beSuccessfulTry
  }

  "verifyQuery . encodeFormQueryVector is right" in prop {
    q: Vector[(String, Option[String])] => verifyQuery(encodeQueryVector(q).encoded) must beSuccessfulTry
  }

  "verifyQuery . encodePlainQueryString is right" in prop {
    s: String => verifyQuery(encodePlainQueryString(s).encoded) must beSuccessfulTry
  }

  "decodePlainQueryString . encodePlainQueryString is right" in prop {
    s: String => decodePlainQueryString(encodePlainQueryString(s)) must_== s
  }
}

class UrlFormSpec extends Http4sSpec with ScalaCheck {
  import UriCodingUtils._

  "decodeQueryParam . encodeQueryParam == id" in prop { (s: String) =>
    decodeQueryParam(encodeQueryParam(s)) == s
  }

  "decodeQueryMapOrdered . encodeQueryMapOrdered == id" in prop {
    q: Map[String, Seq[String]] =>
      (q.nonEmpty ==> (decodeQueryMap(encodeQueryMap(q)) == q))
  }

}
