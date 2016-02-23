package org.http4s.util.encoding

import org.http4s.Http4sSpec
import org.specs2.ScalaCheck
import org.specs2.matcher.DisjunctionMatchers.be_\/-

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
    s: String => verifyUserInfo(encodeUserInfo(s).encoded) must be_\/-
  }

  "verifyRegName . encodeRegName is right" in prop {
    s: String => verifyRegName(encodeRegName(s).encoded) must be_\/-
  }

  "verifyFragment . encodeFragment is right" in prop {
    s: String => verifyFragment(encodeFragment(s).encoded) must be_\/-
  }

  "verifyPath . encodePath is right" in prop {
    p: Seq[String] => verifyPath(encodePath(p).encoded) must be_\/-
  }

  "verifyQuery . encodeQueryMapOrdered is right" in prop {
    q: Map[String, Seq[String]] => verifyQuery(encodeQueryMap(q).encoded) must be_\/-
  }

  "verifyQuery . encodeFormQueryVector is right" in prop {
    q: Vector[(String, Option[String])] => verifyQuery(encodeQueryVector(q).encoded) must be_\/-
  }

  "verifyQuery . encodePlainQueryString is right" in prop {
    s: String => verifyQuery(encodePlainQueryString(s).encoded) must be_\/-
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
