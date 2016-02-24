package org.http4s
package circe

import java.nio.charset.StandardCharsets

import io.circe._
import org.http4s.headers.`Content-Type`
import org.http4s.jawn.JawnDecodeSupportSpec
import org.http4s.EntityEncoderSpec.{writeToByteVector, writeToString}
import org.specs2.specification.core.Fragment

class CirceSpec extends JawnDecodeSupportSpec[Json] with ArbitraryCirceInstances {
  testJsonDecoder(json)

  case class Foo(bar: Int)
  val foo = Foo(42)
  // Beware of possible conflicting shapeless versions if using the circe-generic module
  // to derive these.
  implicit val FooDecoder = Decoder.instance(_.get("bar")(Decoder[Int]).map(Foo))
  implicit val FooEncoder = Encoder.instance[Foo](foo => Json.obj("bar" -> Encoder[Int].apply(foo.bar)))

  "json encoder" should {
    "have json content type" in {
      val json = Json.obj("test" -> Json.string("CirceSupport"))
      jsonEncoder.headers.get(`Content-Type`) must_== Some(`Content-Type`(MediaType.`application/json`))
    }

    "write compact JSON" in prop { json: Json =>
      writeToString(json) must_== Printer.noSpaces.pretty(json)
    }

    "calculate a correct Content-Length" in prop { json: Json =>
      val entity = jsonEncoder.toEntity(json).run
      // val bodyLength = writeToByteVector(entity.body).length
      val bodyLength = Printer.noSpaces.pretty(json).getBytes(StandardCharsets.UTF_8).length
      entity.length must beSome(bodyLength)
    }
  }

  "jsonEncoderOf" should {
    "have json content type" in {
      jsonEncoderOf[Foo].headers.get(`Content-Type`) must_== Some(`Content-Type`(MediaType.`application/json`))
    }

    "write compact JSON" in {
      writeToString(foo)(jsonEncoderOf[Foo]) must_== ("""{"bar":42}""")
    }
  }

  "json" should {
    "decode arbitrary JSON" in prop { j: Json =>
      val req = Request().withBody(Printer.noSpaces.pretty(j)).run
      val decoded = Request().withBody(j).flatMap(json.decode(_, false).run).run
      decoded must be_\/-(j)
    }
  }

  "jsonOf" should {
    "decode JSON from a Circe decoder" in {
      val result = jsonOf[Foo].decode(Request().withBody(Json.obj("bar" -> Json.numberOrNull(42))).run, strict = true)
      result.run.run must be_\/-(Foo(42))
    }

    // https://github.com/http4s/http4s/issues/514
    Fragment.foreach(Seq("ärgerlich", """"ärgerlich"""")) { wort =>
      case class Umlaut(wort: String)
      implicit val umlautDecoder = Decoder.instance(_.get("wort")(Decoder[String]).map(Umlaut))
      s"handle JSON with umlauts: $wort" >> {
        val json = Json.obj("wort" -> Json.string(wort))
        val result = jsonOf[Umlaut].decode(Request().withBody(json).run, strict = true)
        result.run.run must be_\/-(Umlaut(wort))
      }
    }
  }
}
