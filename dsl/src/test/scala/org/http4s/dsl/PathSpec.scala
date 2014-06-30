
/*
* Derived from Twitter Finagle.
*
* Original source:
* https://github.com/twitter/finagle/blob/6e2462acc32ac753bf4e9d8e672f9f361be6b2da/finagle-http/src/test/scala/com/twitter/finagle/http/path/PathSpec.scala
*/
package org.http4s
package dsl

import org.scalatest.{Matchers, WordSpec}

class PathSpec extends WordSpec with Matchers {
  "Path" should {

    "/foo/bar" in {
      Path("/foo/bar").toList should equal (List("foo", "bar"))
    }

    "foo/bar" in {
      Path("foo/bar").toList should equal (List("foo", "bar"))
    }

    ":? extractor" in {
      ((Path("/test.json") :? Map[String, Seq[String]]()) match {
        case Root / "test.json" :? _ => true
        case _                       => false
      }) should be (true)
    }

    ":? extract one parameter" in {
      object Limit extends IntParamMatcher("limit")
      (GET.apply("/hello?limit=1").run match {
        case GET -> Root / "hello" :? Limit(l) => l
      }) should equal(1)
    }

    ":? extract two parameters" in {
      object Start extends IntParamMatcher("start")
      object Limit extends IntParamMatcher("limit")
      (GET.apply("/hello?limit=10&start=1&term=some").run match {
        case GET -> Root / "hello" :? Start(s) :? Limit(l) => s"$s,$l"
      }) should equal("1,10")
    }

    ":? extract many parameters" in {
      object Start extends LongParamMatcher("start")
      object Limit extends LongParamMatcher("limit")
      object SearchTerm extends ParamMatcher("term")
      (GET.apply("/hello?limit=10&start=1&term=some").run match {
        case GET -> Root / "hello" :? Start(s) :? SearchTerm(t) :? Limit(l) => s"$s,$l,$t"
      }) should equal("1,10,some")
    }

    ":? extractor with ParamMatcher" in {
      object A extends ParamMatcher("a")
      object B extends ParamMatcher("b")

      ((Path("/test.json") :? Map[String, Seq[String]]("a" -> Seq("1"), "b" -> Seq("2"))) match {
        case Root / "test.json" :? A(a) => a == "1"
        case _                          => false
      }) should be (true)

      ((Path("/test.json") :? Map[String, Seq[String]]("a" -> Seq("1"), "b" -> Seq("2"))) match {
        case Root / "test.json" :? B(b) => b == "2"
        case _                          => false
      }) should be (true)

      ((Path("/test.json") :? Map[String, Seq[String]]("a" -> Seq("1"), "b" -> Seq("2"))) match {
        case Root / "test.json" :? (A(a) :& B(b)) => a == "1" && b == "2"
        case _                                    => false
      }) should be (true)

      ((Path("/test.json") :? Map[String, Seq[String]]("a" -> Seq("1"), "b" -> Seq("2"))) match {
        case Root / "test.json" :? (B(b) :& A(a)) => a == "1" && b == "2"
        case _                                    => false
      }) should be (true)
    }

    ":? extractor with IntParamMatcher and LongParamMatcher" in {
      object I extends IntParamMatcher("i")
      object L extends LongParamMatcher("l")
      object D extends DoubleParamMatcher("d")

      ((Path("/test.json") :? Map[String, Seq[String]]("i" -> Seq("1"), "l" -> Seq("2147483648"), "d" -> Seq("1.3"))) match {
        case Root / "test.json" :? (I(i) :& L(l) :& D(d)) => i == 1 && l == 2147483648L && d == 1.3D
        case _                                    => false
      }) should be (true)
    }

    "~ extractor on Path" in {
      (Path("/foo.json") match {
        case Root / "foo" ~ "json" => true
        case _                     => false
      }) should be (true)
    }

    "~ extractor on filename foo.json" in {
      ("foo.json" match {
        case "foo" ~ "json" => true
        case _              => false
      }) should be (true)
    }

    "~ extractor on filename foo" in {
      ("foo" match {
        case "foo" ~ "" => true
        case _          => false
      }) should be (true)
    }

    "-> extractor /test.json" in {
      val req = Request(requestMethod = Method.Get, requestUri = Uri.fromString("/test.json").get)
      (req match {
        case GET -> Root / "test.json" => true
        case _                         => false
      }) should be (true)
    }

    "-> extractor /foo/test.json" in {
      val req = Request(requestMethod = Method.Get, requestUri = Uri.fromString("/foo/test.json").get)
      (req match {
        case GET -> Root / "foo" / "test.json" => true
        case _                         => false
      }) should be (true)
    }

   "request path info extractor for /" in {
      val req = Request(requestMethod = Method.Get, requestUri = Uri.fromString("/").get)
      (req match {
        case _ -> Root => true
        case _ => false
      }) should be (true)
    }

    "Root extractor" in {
      (Path("/") match {
        case Root => true
        case _    => false
      }) should be (true)
    }

    "Root extractor, no partial match" in {
      (Path("/test.json") match {
        case Root => true
        case _    => false
      }) should be (false)
    }

    "Root extractor, empty path" in {
      (Path("") match {
        case Root => true
        case _    => false
      }) should be (true)
    }

    "/ extractor" in {
      (Path("/1/2/3/test.json") match {
        case Root / "1" / "2" / "3" / "test.json" => true
        case _                                    => false
      }) should be (true)
    }

    "Int extractor" in {
      (Path("/user/123") match {
        case Root / "user" / IntVar(userId) => userId == 123
        case _                                => false
      }) should be (true)
    }

    "Int extractor, invalid int" in {
      (Path("/user/invalid") match {
        case Root / "user" / IntVar(userId) => true
        case _                                => false
      }) should be (false)
    }

    "Int extractor, number format error" in {
      (Path("/user/2147483648") match {
        case Root / "user" / IntVar(userId) => true
        case _                                => false
      }) should be (false)
    }

    "Long extractor" in {
      (Path("/user/123") match {
        case Root / "user" / LongVar(userId) => userId == 123
        case _                                 => false
      }) should be (true)
    }

    "Long extractor, invalid int" in {
      (Path("/user/invalid") match {
        case Root / "user" / LongVar(userId) => true
        case _                                 => false
      }) should be (false)
    }

    "Long extractor, number format error" in {
      (Path("/user/9223372036854775808") match {
        case Root / "user" / LongVar(userId) => true
        case _                                 => false
      }) should be (false)
    }
  }



//  "Method extractors" should {
//    "match relative to path info" in {
//      (Request(requestMethod = GET, scriptName = "/script-name", pathInfo = "/path-info") match {
//        case GET -> Root / "path-info" => true
//        case _ => false
//      }) should be (true)
//    }
//  }
}
