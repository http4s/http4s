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
package blazecore

import cats.syntax.all._
import fs2._
import org.http4s.blaze.http.parser.Http1ClientParser
import org.typelevel.ci.CIString

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import scala.collection.mutable.ListBuffer

class ResponseParser extends Http1ClientParser {
  private val headers = new ListBuffer[(String, String)]

  private var code: Int = -1
  private var reason = ""
  private var scheme = ""
  private var majorversion = -1
  private var minorversion = -1

  /** Will not mutate the ByteBuffers in the Seq */
  def parseResponse(buffs: Seq[ByteBuffer]): (Status, Set[Header.Raw], String) = {
    val b =
      ByteBuffer.wrap(buffs.map(b => Chunk.byteBuffer(b).toArray).toArray.flatten)
    parseResponseBuffer(b)
  }

  /* Will mutate the ByteBuffer */
  def parseResponseBuffer(buffer: ByteBuffer): (Status, Set[Header.Raw], String) = {
    parseResponseLine(buffer)
    parseHeaders(buffer)

    if (!headersComplete()) sys.error("Headers didn't complete!")
    val body = new ListBuffer[ByteBuffer]
    while (!this.contentComplete() && buffer.hasRemaining)
      body += parseContent(buffer)

    val bp = {
      val bytes =
        body.toList.foldLeft(Vector.empty[Chunk[Byte]])((vec, bb) => vec :+ Chunk.byteBuffer(bb))
      new String(Chunk.concatBytes(bytes).toArray, StandardCharsets.ISO_8859_1)
    }

    val headers: Set[Header.Raw] = this.headers
      .result()
      .toSet
      .map { (kv: (String, String)) =>
        Header.Raw(CIString(kv._1), kv._2)
      }

    val status = Status.fromInt(this.code).valueOr(throw _)

    (status, headers, bp)
  }

  override protected def headerComplete(name: String, value: String): Boolean = {
    headers += ((name, value))
    false
  }

  override protected def submitResponseLine(
      code: Int,
      reason: String,
      scheme: String,
      majorversion: Int,
      minorversion: Int,
  ): Unit = {
    this.code = code
    this.reason = reason
    this.majorversion = majorversion
    this.minorversion = minorversion
  }
}

object ResponseParser {
  def apply(buff: Seq[ByteBuffer]): (Status, Set[Header.Raw], String) =
    new ResponseParser().parseResponse(buff)
  def apply(buff: ByteBuffer): (Status, Set[Header.Raw], String) = parseBuffer(buff)

  def parseBuffer(buff: ByteBuffer): (Status, Set[Header.Raw], String) =
    new ResponseParser().parseResponseBuffer(buff)
}
