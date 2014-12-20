package org.http4s

import org.specs2.ScalaCheck

import scalaz.\/-


class UrlFormSpec extends Http4sSpec with ScalaCheck {

  "UrlForm" should {

    "decode . encode == right" in prop{ (urlForm: UrlForm, charset: Charset) =>
      UrlForm.entityDecoder(Charset.`UTF-8`).decode(
        Request().withBody(urlForm)(UrlForm.entityEncoder(Charset.`UTF-8`)).run
      ).run.run must_== \/-(urlForm)
    }

  }

}
