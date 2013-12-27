package org.http4s.parser

import org.scalatest.{Matchers, WordSpec}
import org.http4s.Header.`Content-Type`
import org.http4s.MediaType._
import org.http4s.CharacterSet
import scalaz.Validation

/**
 * @author Bryce Anderson
 *         Created on 12/26/13
 */
class ContentTypeHeaderSpec  extends WordSpec with Matchers with HeaderParserHelper[`Content-Type`] {

  def hparse(value: String): Validation[ParseErrorInfo, `Content-Type`] = HttpParser.CONTENT_TYPE(value)

  def simple = `Content-Type`(`text/html`)
  def charset = `Content-Type`(`text/html`, CharacterSet.`UTF-8`)
  def extensions = `Content-Type`(`text/html`.withExtensions(Map("foo" -> "bar")))
  def extensionsandset = `Content-Type`(`text/html`.withExtensions(Map("foo" -> "bar")), CharacterSet.`UTF-8`)

  "ContentType Header" should {
    "Generate the correct values" in {
      simple.value should equal("text/html")
      charset.value should equal("text/html; charset=UTF-8")
      extensions.value should equal("text/html; foo=bar")
      extensionsandset.value should equal("text/html; foo=bar; charset=UTF-8")
    }

    "Parse correctly" in {
      parse(simple.value) should equal(simple)
      parse(charset.value) should equal(charset)
      parse(extensions.value) should equal(extensions)
      parse(extensionsandset.value) should equal(extensionsandset)
    }
  }

}
