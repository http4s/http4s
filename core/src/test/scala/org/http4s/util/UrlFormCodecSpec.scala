package org.http4s.util

import org.http4s.Http4sSpec
import org.http4s.util.UrlFormCodec._


class UrlFormCodecSpec extends Http4sSpec {

  "UrlFormCodec" should {
    // ----------------- Form decoding ------------------------
    "Decode a simple result" in {
      val encoded = "Age=23"
      val result = Map(("Age",Seq("23")))

      decode(encoded).yolo must_== result
    }

    "Decode a param with spaces" in {
      val encoded = "Name=Jonathan+Doe"
      val result = Map(("Name",Seq("Jonathan Doe")))

      decode(encoded).yolo must_== result
    }

    "Decode a param with '+' symbols" in {
      val encoded = "Formula=a+%2B+b"
      val result = Map(("Formula",Seq("a + b")))

      decode(encoded).yolo must_== result
    }

    "Decode a param with special symbols" in {
      val encoded = "Formula=a+%2B+b+%3D%3D+13%25%21"
      val result = Map(("Formula",Seq("a + b == 13%!")))

      decode(encoded).yolo must_== result
    }

    "Decode many params" in {
      val encoded = "Name=Jonathan+Doe&Age=23&Formula=a+%2B+b+%3D%3D+13%25%21"
      val result = Map(("Formula",Seq("a + b == 13%!")),
                       ("Age",Seq("23")),
                       ("Name",Seq("Jonathan Doe")))

      decode(encoded).yolo must_== result
    }

    // ----------------- Form encoding ------------------------
    "Encode a simple parameter" in {
      val encoded = "Age=23"
      val result = Map(("Age",Seq("23")))

      encode(result) must_== encoded
    }

    "Encode a param with spaces" in {
      val encoded = "Name=Jonathan+Doe"
      val result = Map(("Name",Seq("Jonathan Doe")))

      encode(result) must_== encoded
    }

    "Encode a param with '+' symbols" in {
      val encoded = "Formula=a+%2B+b"
      val result = Map(("Formula",Seq("a + b")))

      encode(result) must_== encoded
    }

    "Encode a param with special symbols" in {
      val encoded = "Formula=a+%2B+b+%3D%3D+13%25%21"
      val result = Map(("Formula",Seq("a + b == 13%!")))

      encode(result) must_== encoded
    }

    "Do a round trip" in {
      val result = Map(("Formula",Seq("a + b == 13%!")),
        ("Age",Seq("23")),
        ("Name",Seq("Jonathan Doe")))

      decode(encode(result)).yolo must_== result
    }
  }

}
