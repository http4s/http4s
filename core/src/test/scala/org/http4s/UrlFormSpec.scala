package org.http4s

import org.specs2.ScalaCheck

import scalaz.\/-


class UrlFormSpec extends Http4sSpec with ScalaCheck {

  def checkDecode(encoded: String, expected: UrlForm) =
    ResponseBuilder(Status.Ok, encoded).flatMap(
      UrlForm.entityDecoder(Charset.`UTF-8`).decode(_).run
    ).run must_== \/-(expected)

  def checkEncode(urlForm: UrlForm, expected: String) =
    (for {
      encodedUrlForm  <- UrlForm.entityEncoder(Charset.`UTF-8`).toEntity(urlForm).flatMap(_.body.runLog).map(_.reduce(_ ++ _))
      encodedExpected <- EntityEncoder.stringEncoder.toEntity(expected).flatMap(_.body.runLog).map(_.reduce(_ ++ _))
    } yield encodedUrlForm must_== encodedExpected).run


  "UrlForm" should {

    "decode everything it encoded" in prop{ (urlForm: UrlForm, charset: Charset) =>
      UrlForm.entityDecoder(Charset.`UTF-8`).decode(
        Request().withBody(urlForm)(UrlForm.entityEncoder(Charset.`UTF-8`)).run
      ).run.run must_== \/-(urlForm)
    }

    // ----------------- Form decoding ------------------------
    "Decode a simple result" in {
      checkDecode(
        encoded  = "Age=23",
        expected = UrlForm(Map("Age" -> Seq("23")))
      )
    }

    "Decode a param with spaces" in {
      checkDecode(
        encoded  = "Name=Jonathan+Doe",
        expected = UrlForm(Map("Name" -> Seq("Jonathan Doe")))
      )
    }

    "Decode a param with '+' symbols" in {
      checkDecode(
        encoded  =  "Formula=a+%2B+b",
        expected = UrlForm(Map("Formula" -> Seq("a + b")))
      )
    }

    "Decode a param with special symbols" in {
      checkDecode(
        encoded  = "Formula=a+%2B+b+%3D%3D+13%25%21",
        expected = UrlForm(Map("Formula" -> Seq("a + b == 13%!")))
      )
    }

    "Decode many params" in {
      checkDecode(
        encoded  = "Name=Jonathan+Doe&Age=23&Formula=a+%2B+b+%3D%3D+13%25%21",
        expected = UrlForm(Map(
          "Formula" -> Seq("a + b == 13%!"),
          "Age"     -> Seq("23"),
          "Name"    -> Seq("Jonathan Doe")
        ))
      )
    }

    // ----------------- Form encoding ------------------------
    "Encode a simple parameter" in {
      checkEncode(
        urlForm  = UrlForm(Map("Age" -> Seq("23"))),
        expected = "Age=23"
      )
    }

    "Encode a param with spaces" in {
      checkEncode(
        urlForm  = UrlForm(Map("Name" -> Seq("Jonathan Doe"))),
        expected = "Name=Jonathan+Doe"
      )
    }

    "Encode a param with '+' symbols" in {
      checkEncode(
        urlForm  = UrlForm(Map("Formula" -> Seq("a + b"))),
        expected = "Formula=a+%2B+b"
      )
    }

    "Encode a param with special symbols" in {
      checkEncode(
        urlForm  = UrlForm(Map("Formula" -> Seq("a + b == 13%!"))),
        expected = "Formula=a+%2B+b+%3D%3D+13%25%21"
      )
    }

  }

}
