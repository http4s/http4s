package org.http4s
package scalaxml

import java.io.StringReader

import headers.`Content-Type`
import scala.util.control.NonFatal
import scala.xml._
import scalaz.concurrent.Task
import scalaz.stream.io.toInputStream

trait ElemInstances {
  implicit def xmlEnocder(implicit charset: Charset = DefaultCharset): EntityEncoder[Elem] =
    EntityEncoder.stringEncoder(charset)
      .contramap[Elem](xml => xml.buildString(false))
      .withContentType(`Content-Type`(MediaType.`application/xml`))

  /**
   * Handles a message body as XML.
   *
   * @param parser the SAX parser to use to parse the XML
   * @return an XML element
   */
  implicit def xml(implicit parser: SAXParser = XML.parser): EntityDecoder[Elem] = {
    import EntityDecoder._
    decodeBy(MediaType.`text/xml`, MediaType.`text/html`, MediaType.`application/xml`) { msg =>
      val stream = toInputStream(msg.body)
      val source = new InputSource(stream)
      msg.charset.foreach(cs => source.setEncoding(cs.nioCharset.name))
      try DecodeResult.success(XML.loadXML(source, parser))
      catch {
        case e: SAXParseException =>
          DecodeResult.failure(MalformedMessageBodyFailure("Invalid XML", Some(e)))
        case NonFatal(e) =>
          DecodeResult(Task.fail(e))
      }
    }
  }
}
