package org.http4s
package parser

import org.http4s.headers.`Content-Type`
import org.http4s.MediaType
import org.specs2.mutable.Specification

class ContentTypeHeaderSpec extends Specification with HeaderParserHelper[`Content-Type`] {

  def hparse(value: String): ParseResult[`Content-Type`] = HttpHeaderParser.CONTENT_TYPE(value)

  def simple = `Content-Type`(MediaType.text.html)
  def charset = `Content-Type`(MediaType.text.html, Charset.`UTF-8`)
  def extensions = `Content-Type`(MediaType.text.html.withExtensions(Map("foo" -> "bar")))
  def extensionsandset =
    `Content-Type`(MediaType.text.html.withExtensions(Map("foo" -> "bar")), Charset.`UTF-8`)
  def multipart =
    `Content-Type`(
      MediaType.multipart.`form-data`.withExtensions(Map("boundary" -> "aLotOfMoose")),
      Charset.`UTF-8`)

  "ContentType Header" should {
    "Generate the correct values" in {
      simple.value must be_==("text/html")
      charset.value must be_==("""text/html; charset=UTF-8""")
      extensions.value must be_==("""text/html; foo="bar"""")
      extensionsandset.value must be_==("""text/html; foo="bar"; charset=UTF-8""")
      multipart.value must be_==("""multipart/form-data; boundary="aLotOfMoose"; charset=UTF-8""")
    }

    "Parse correctly" in {
      parse(simple.value) must be_==(simple)
      parse(charset.value) must be_==(charset)
      parse(extensions.value) must be_==(extensions)
      parse(extensionsandset.value) must be_==(extensionsandset)
      parse(multipart.value) must be_==(multipart)
    }
  }

}
