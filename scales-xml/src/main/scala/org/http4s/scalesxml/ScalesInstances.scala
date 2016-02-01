package org.http4s
package scalesxml

import java.io.ByteArrayInputStream

import org.http4s.headers.`Content-Type`
import org.xml.sax.{InputSource, SAXParseException}

import scala.util.control.NonFatal
import scalaz.concurrent.Task
import scales.xml.serializers.{SerializeableXml, SerializerFactory}
import scales.xml.{asString, Doc, loadXml, XmlVersion}


trait ScalesInstances {

  implicit def xmlDocEncoder(implicit charset: Charset = DefaultCharset, serf : SerializerFactory, sxml : SerializeableXml[Doc]): EntityEncoder[Doc] =
    EntityEncoder.stringEncoder(charset)
      .contramap[Doc](xml => asString(xml))
      .withContentType(`Content-Type`(MediaType.`application/xml`))


  implicit def xmlDocDecoder(implicit xmlVer: XmlVersion): EntityDecoder[Doc] = {
    import EntityDecoder._
    decodeBy(MediaType.`text/xml`, MediaType.`text/html`, MediaType.`application/xml`){ msg =>
      collectBinary(msg).flatMap[Doc] { arr =>
        val source = new InputSource(new ByteArrayInputStream(arr.toArray))
        try DecodeResult.success(Task.now(loadXml(source)))
        catch {
          case e: SAXParseException =>
            val msg = s"${e.getMessage}; Line: ${e.getLineNumber}; Column: ${e.getColumnNumber}"
            DecodeResult.failure(Task.now(ParseFailure("Invalid XML", msg)))
          case NonFatal(e) => DecodeResult(Task.fail(e))
        }
      }
    }
  }
}
