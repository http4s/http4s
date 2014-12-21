package org.http4s

import org.specs2.ScalaCheck

import scalaz.\/-


class UrlFormSpec extends Http4sSpec with ScalaCheck {

  "UrlForm" should {

    "decode . encode == right" in prop{ (urlForm: UrlForm, charset: Charset) =>
      UrlForm.entityDecoder.decode(
        Request().withBody(urlForm)(UrlForm.entityEncoder(charset)).run
      ).run.run must_== \/-(urlForm)
    }

    "decodeString . encodeString == right" in prop{ (urlForm: UrlForm, charset: Charset) =>
      UrlForm.decodeString(charset)(
        UrlForm.encodeString(charset)(urlForm)
      ) must_== \/-(urlForm)
    }

  }

}
