package org.http4s
package scalaxml

import cats._
import cats.effect._
import cats.implicits._
import java.io.StringReader
import javax.xml.parsers.SAXParserFactory

import org.http4s.headers.`Content-Type`
import org.http4s.util.FEither._
import scala.util.control.NonFatal
import scala.xml._

trait ElemInstances {
  protected def saxFactory: SAXParserFactory

  implicit def xmlEncoder[F[_]: Applicative](
      implicit charset: Charset = DefaultCharset): EntityEncoder[F, Elem] =
    EntityEncoder
      .stringEncoder[F]
      .contramap[Elem](xml => xml.buildString(false))
      .withContentType(`Content-Type`(MediaType.application.xml))

  /**
    * Handles a message body as XML.
    *
    * TODO Not an ideal implementation.  Would be much better with an asynchronous XML parser, such as Aalto.
    *
    * @return an XML element
    */
  implicit def xml[F[_]](implicit F: Sync[F]): EntityDecoder[F, Elem] = {
    import EntityDecoder._
    decodeBy(MediaType.text.xml, MediaType.text.html, MediaType.application.xml) { msg =>
      collectBinary(msg).rightFlatMap { chunk =>
        val source = new InputSource(
          new StringReader(
            new String(chunk.toArray, msg.charset.getOrElse(Charset.`US-ASCII`).nioCharset)))
        val saxParser = saxFactory.newSAXParser()
        F.delay(XML.loadXML(source, saxParser))
          .attempt
          .leftFlatMap {
            case e: SAXParseException =>
              F.pure(Left(MalformedMessageBodyFailure("Invalid XML", Some(e))))
            case NonFatal(e) => F.raiseError[Either[DecodeFailure, Elem]](e)
          }
      }

    }
  }
}
