package org.http4s
package headers

import org.http4s.MediaType._

class ContentTypeSpec extends HeaderParserSpec(`Content-Type`) {

  def simple = `Content-Type`(`text/html`)
  def charset = `Content-Type`(`text/html`, Charset.`UTF-8`)
  def extensions = `Content-Type`(`text/html`.withExtensions(Map("foo" -> "bar")))
  def extensionsandset = `Content-Type`(`text/html`.withExtensions(Map("foo" -> "bar")), Charset.`UTF-8`)

  "ContentType Header" should {
    "Generate the correct values" in {
      simple.value must be_==("text/html")
      charset.value must be_==("text/html; charset=UTF-8")
      extensions.value must be_==("text/html; foo=bar")
      extensionsandset.value must be_==("text/html; foo=bar; charset=UTF-8")
    }

    "Parse correctly" in {
      parse(simple.value) must be_==(simple)
      parse(charset.value) must be_==(charset)
      parse(extensions.value) must be_==(extensions)
      parse(extensionsandset.value) must be_==(extensionsandset)
    }
  }

}
