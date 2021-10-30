/*
 * Copyright 2014 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s
package scalaxml

import cats.data.EitherT
import cats.effect.Sync
import cats.syntax.all._
import org.http4s.Charset.`UTF-8`
import org.http4s.headers.`Content-Type`

import java.io.ByteArrayInputStream
import java.io.StringWriter
import javax.xml.parsers.SAXParserFactory
import scala.util.control.NonFatal
import scala.xml.Elem
import scala.xml.InputSource
import scala.xml.SAXParseException
import scala.xml.XML

trait ElemInstances {
  protected def saxFactory: SAXParserFactory

  implicit def xmlEncoder[F[_]](implicit charset: Charset = `UTF-8`): EntityEncoder[F, Elem] =
    EntityEncoder
      .stringEncoder[F]
      .contramap[Elem] { node =>
        val sw = new StringWriter
        XML.write(sw, node, charset.nioCharset.name, true, null)
        sw.toString
      }
      .withContentType(`Content-Type`(MediaType.application.xml).withCharset(charset))

  /** Handles a message body as XML.
    *
    * TODO Not an ideal implementation.  Would be much better with an asynchronous XML parser, such as Aalto.
    *
    * @return an XML element
    */
  implicit def xml[F[_]](implicit F: Sync[F]): EntityDecoder[F, Elem] = {
    import EntityDecoder._
    decodeBy(MediaType.text.xml, MediaType.text.html, MediaType.application.xml) { msg =>
      val source = new InputSource()
      msg.charset.foreach(cs => source.setEncoding(cs.nioCharset.name))

      collectBinary(msg).flatMap[DecodeFailure, Elem] { chunk =>
        source.setByteStream(new ByteArrayInputStream(chunk.toArray))
        val saxParser = saxFactory.newSAXParser()
        EitherT(
          F.delay(XML.loadXML(source, saxParser)).attempt
        ).leftFlatMap {
          case e: SAXParseException =>
            DecodeResult.failureT(MalformedMessageBodyFailure("Invalid XML", Some(e)))
          case NonFatal(e) => DecodeResult(F.raiseError[Either[DecodeFailure, Elem]](e))
        }
      }
    }
  }
}
