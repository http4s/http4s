package org.http4s
package circe

import java.nio.charset.StandardCharsets

import io.circe._
import Status.Ok
import org.http4s.headers.`Content-Type`
import org.http4s.jawn.JawnDecodeSupportSpec
import org.http4s.EntityEncoderSpec.writeToString
import org.specs2.specification.core.Fragment

// Originally based on ArgonautSpec
class CirceSpec extends JawnDecodeSupportSpec[Json] {
  testJsonDecoder(jsonDecoder)

  sealed case class Foo(bar: Int)
  val foo = Foo(42)
  // Beware of possible conflicting shapeless versions if using the circe-generic module
  // to derive these.
  implicit val FooDecoder: Decoder[Foo] =
    Decoder.forProduct1("bar")(Foo.apply)
  implicit val FooEncoder: Encoder[Foo] =
    Encoder.forProduct1("bar")(foo => (foo.bar))

  case class FooBar(foo: Int, bar: Option[Int])
  val fooBar = FooBar(42, None)
  implicit val FooBarDecoder = Decoder.forProduct2[Int, Option[Int], FooBar]("foo", "bar")(FooBar.apply)
  implicit val FooBarEncoder = Encoder.forProduct2[Int, Option[Int], FooBar]("foo", "bar")(t => (t.foo, t.bar))

  "json encoder" should {
    val json = Json.obj("test" -> Json.fromString("CirceSupport"))

    "have json content type" in {
      jsonEncoder.headers.get(`Content-Type`) must_== Some(`Content-Type`(MediaType.`application/json`))
    }

    "write compact JSON" in {
      writeToString(json) must_== """{"test":"CirceSupport"}"""
    }
  }

  "jsonEncoderOf" should {
    "have json content type" in {
      jsonEncoderOf[Foo].headers.get(`Content-Type`) must_== Some(`Content-Type`(MediaType.`application/json`))
    }

    "write compact JSON" in {
      writeToString(foo)(jsonEncoderOf[Foo]) must_== """{"bar":42}"""
    }

    "allow excluding null fields" in {
      val myCustomInstances = new CirceInstances {
        implicit val printer: Printer = Printer.noSpaces.copy(dropNullKeys = true)
      }
      import myCustomInstances._

      writeToString(fooBar) must_== """{"foo":42}"""
    }
  }

  "json" should {
    "handle the optionality of asNumber" in {
      // From ArgonautSpec, which tests similar things:
      // TODO Urgh.  We need to make testing these smoother.
      // https://github.com/http4s/http4s/issues/157
      def getBody(body: EntityBody): Array[Byte] = body.runLog.run.reduce(_ ++ _).toArray
      val req = Request().withBody(Json.fromDoubleOrNull(157)).run
      val body = req.decode { json: Json => Response(Ok).withBody(json.asNumber.flatMap(_.toLong).getOrElse(0L).toString)}.run.body
      new String(getBody(body), StandardCharsets.UTF_8) must_== "157"
    }
  }

  "jsonOf" should {
    "decode JSON from a Circe decoder" in {
      val result = jsonOf[Foo].decode(Request().withBody(Json.obj("bar" -> Json.fromDoubleOrNull(42))).run, strict = true)
      result.run.run must be_\/-(Foo(42))
    }

    // https://github.com/http4s/http4s/issues/514
    Fragment.foreach(Seq("ärgerlich", """"ärgerlich"""")) { wort =>
      sealed case class Umlaut(wort: String)
      implicit val umlautDecoder =
        Decoder.forProduct1("wort")(Umlaut.apply)
      s"handle JSON with umlauts: $wort" >> {
        val json = Json.obj("wort" -> Json.fromString(wort))
        val result = jsonOf[Umlaut].decode(Request().withBody(json).run, strict = true)
        result.run.run must be_\/-(Umlaut(wort))
      }
    }
  }
}
