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
package blaze.server

import cats.effect._
import cats.syntax.all._
import org.log4s.Logger
import org.typelevel.vault._

import java.nio.ByteBuffer
import scala.collection.mutable.ListBuffer
import scala.util.Either

private[http4s] final class Http1ServerParser[F[_]](
    logger: Logger,
    maxRequestLine: Int,
    maxHeadersLen: Int)(implicit F: Effect[F])
    extends blaze.http.parser.Http1ServerParser(maxRequestLine, maxHeadersLen, 2 * 1024) {
  private var uri: String = _
  private var method: String = _
  private var minor: Int = -1
  private val headers = new ListBuffer[Header.ToRaw]

  def minorVersion(): Int = minor

  def doParseRequestLine(buff: ByteBuffer): Boolean = parseRequestLine(buff)

  def doParseHeaders(buff: ByteBuffer): Boolean = parseHeaders(buff)

  def doParseContent(buff: ByteBuffer): Option[ByteBuffer] = Option(parseContent(buff))

  def collectMessage(
      body: EntityBody[F],
      attrs: Vault): Either[(ParseFailure, HttpVersion), Request[F]] = {
    val h = Headers(headers.result())
    headers.clear()
    val protocol = if (minorVersion() == 1) HttpVersion.`HTTP/1.1` else HttpVersion.`HTTP/1.0`

    val attrsWithTrailers =
      if (minorVersion() == 1 && isChunked)
        attrs.insert(
          Message.Keys.TrailerHeaders[F],
          F.defer[Headers] {
            if (!contentComplete())
              F.raiseError(
                new IllegalStateException(
                  "Attempted to collect trailers before the body was complete."))
            else F.pure(Headers(headers.result()))
          }
        )
      else attrs // Won't have trailers without a chunked body

    Method
      .fromString(this.method)
      .flatMap { method =>
        Uri.requestTarget(this.uri).map { uri =>
          Request(method, uri, protocol, h, body, attrsWithTrailers)
        }
      }
      .leftMap(_ -> protocol)
  }

  override def submitRequestLine(
      methodString: String,
      uri: String,
      scheme: String,
      majorversion: Int,
      minorversion: Int): Boolean = {
    logger.trace(s"Received request($methodString $uri $scheme/$majorversion.$minorversion)")
    this.uri = uri
    this.method = methodString
    this.minor = minorversion
    false
  }

  // ///////////////// Stateful methods for the HTTP parser ///////////////////
  override protected def headerComplete(name: String, value: String): Boolean = {
    logger.trace(s"Received header '$name: $value'")
    headers += name -> value
    false
  }

  override def reset(): Unit = {
    uri = null
    method = null
    minor = -1
    headers.clear()
    super.reset()
  }
}
