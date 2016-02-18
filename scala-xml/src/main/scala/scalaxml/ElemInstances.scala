package org.http4s
package scalaxml

import java.io.StringReader

import headers.`Content-Type`
import scala.util.control.NonFatal
import scala.xml._
import scalaz.concurrent.Task

trait ElemInstances {
  implicit def xmlEnocder(implicit charset: Charset = DefaultCharset): EntityEncoder[Elem] =
    EntityEncoder.stringEncoder(charset)
      .contramap[Elem](xml => xml.buildString(false))
      .withContentType(`Content-Type`(MediaType.`application/xml`))

  /**
   * Handles a message body as XML.
   *
   * TODO Not an ideal implementation.  Would be much better with an asynchronous XML parser, such as Aalto.
   *
   * @param parser the SAX parser to use to parse the XML
   * @return an XML element
   */
  implicit def xml(implicit parser: SAXParser = XML.parser): EntityDecoder[Elem] = {
    import EntityDecoder._
    decodeBy(MediaType.`text/xml`, MediaType.`text/html`, MediaType.`application/xml`){ msg =>
      collectBinary(msg).flatMap[Elem] { arr =>
        val source = new InputSource(new StringReader(new String(arr.toArray, msg.charset.getOrElse(Charset.`US-ASCII`).nioCharset)))
        try DecodeResult.success(Task.now(XML.loadXML(source, parser)))
        catch {
          case e: SAXParseException =>
            DecodeResult.failure(MalformedMessageBodyFailure("Invalid XML", Some(e)))
          case NonFatal(e) => DecodeResult(Task.fail(e))
        }
      }
    }
  }
}
