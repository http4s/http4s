/*
 * Derived from https://github.com/spray/spray/blob/v1.1-M7/spray-http/src/main/scala/spray/http/HttpEncoding.scala
 *
 * Copyright (C) 2011-2012 spray.io
 * Based on code copyright (C) 2010-2011 by the BlueEyes Web Framework Team (http://github.com/jdegoes/blueeyes)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.http4s

import org.http4s.syntax.string._
import org.http4s.util._
import cats.Eq

final case class TransferCoding private (coding: CaseInsensitiveString) extends Renderable {
  override def render(writer: Writer): writer.type = writer.append(coding.toString)
}

object TransferCoding {
  implicit class CI2TransferCoding(val ci: CaseInsensitiveString) extends AnyVal {
    def toTransferCoding: TransferCoding = TransferCoding(ci)
  }

  implicit val eq: Eq[TransferCoding] = Eq.fromUniversalEquals

  // http://www.iana.org/assignments/http-parameters/http-parameters.xml#http-parameters-2
  val chunked = TransferCoding("chunked".ci)
  val compress = TransferCoding("compress".ci)
  val deflate = TransferCoding("deflate".ci)
  val gzip = TransferCoding("gzip".ci)
  val identity = TransferCoding("identity".ci)
}
