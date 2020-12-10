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

package org.http4s.client.blaze

import cats.syntax.all._
import java.nio.ByteBuffer
import org.http4s._
import org.http4s.blaze.http.parser.Http1ClientParser
import scala.collection.mutable.ListBuffer

private[blaze] final class BlazeHttp1ClientParser(
    maxResponseLineSize: Int,
    maxHeaderLength: Int,
    maxChunkSize: Int,
    parserMode: ParserMode)
    extends Http1ClientParser(
      maxResponseLineSize,
      maxHeaderLength,
      2 * 1024,
      maxChunkSize,
      parserMode == ParserMode.Lenient) {
  private val headers = new ListBuffer[Header]
  private var status: Status = _
  private var httpVersion: HttpVersion = _

  override def reset(): Unit = {
    headers.clear()
    status = null
    httpVersion = null
    super.reset()
  }

  def getHttpVersion(): HttpVersion =
    if (httpVersion == null) HttpVersion.`HTTP/1.0` // TODO Questionable default
    else httpVersion

  def doParseContent(buffer: ByteBuffer): Option[ByteBuffer] = Option(parseContent(buffer))

  def getHeaders(): Headers =
    if (headers.isEmpty) Headers.empty
    else {
      val hs = Headers(headers.result())
      headers.clear() // clear so we can accumulate trailing headers
      hs
    }

  def getStatus(): Status =
    if (status == null) Status.InternalServerError
    else status

  def finishedResponseLine(buffer: ByteBuffer): Boolean =
    responseLineComplete() || parseResponseLine(buffer)

  def finishedHeaders(buffer: ByteBuffer): Boolean =
    headersComplete() || parseHeaders(buffer)

  override protected def submitResponseLine(
      code: Int,
      reason: String,
      scheme: String,
      majorversion: Int,
      minorversion: Int): Unit = {
    status = Status.fromIntAndReason(code, reason).valueOr(throw _)
    httpVersion =
      if (majorversion == 1 && minorversion == 1) HttpVersion.`HTTP/1.1`
      else if (majorversion == 1 && minorversion == 0) HttpVersion.`HTTP/1.0`
      else HttpVersion.fromVersion(majorversion, minorversion).getOrElse(HttpVersion.`HTTP/1.0`)
  }

  override protected def headerComplete(name: String, value: String): Boolean = {
    headers += Header(name, value)
    false
  }
}
