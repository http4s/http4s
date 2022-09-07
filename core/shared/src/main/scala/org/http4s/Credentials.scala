/*
 * Copyright 2013 http4s.org
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

/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Based on https://github.com/spray/spray/blob/v1.1-M7/spray-http/src/main/scala/spray/http/HttpCredentials.scala
 * Copyright (C) 2011-2012 spray.io
 * Based on code copyright (C) 2010-2011 by the BlueEyes Web Framework Team
 */

package org.http4s

import cats.data.NonEmptyList
import org.http4s.util.Renderable
import org.http4s.util.Writer

import java.nio.ByteBuffer
import java.nio.charset.CharsetDecoder
import java.nio.charset.StandardCharsets
import java.nio.charset.{Charset => JavaCharset}
import java.util.Base64
import scala.util.Try

sealed abstract class Credentials extends Renderable {
  def authScheme: AuthScheme
}

object Credentials {
  final case class Token(authScheme: AuthScheme, token: String) extends Credentials {
    def render(writer: Writer): writer.type =
      writer << authScheme << ' ' << token
  }

  final case class AuthParams(authScheme: AuthScheme, params: NonEmptyList[(String, String)])
      extends Credentials {
    def render(writer: Writer): writer.type = {
      writer << authScheme
      writer << ' '

      def renderParam(k: String, v: String) = {
        writer << k << '='
        writer.quote(v)
        ()
      }
      renderParam(params.head._1, params.head._2)
      params.tail.foreach { case (k, v) =>
        writer.append(',')
        renderParam(k, v)
      }
      writer
    }
  }

  object AuthParams {
    def apply(
        authScheme: AuthScheme,
        param: (String, String),
        params: (String, String)*
    ): AuthParams =
      apply(authScheme, NonEmptyList(param, params.toList))
  }
}

final case class BasicCredentials(
    username: String,
    password: String,
    charset: JavaCharset = StandardCharsets.UTF_8,
) {
  lazy val token: String = {
    val userPass = username + ':' + password
    val bytes = userPass.getBytes(charset)
    Base64.getEncoder.encodeToString(bytes)
  }
}

object BasicCredentials {

  private val utf8Charset = StandardCharsets.UTF_8
  private val utf8CharsetDecoder = utf8Charset.newDecoder
  private val fallbackCharset = StandardCharsets.ISO_8859_1

  private def decode(bytes: Array[Byte], decoder: CharsetDecoder): Try[String] = {
    val byteByffer = ByteBuffer.wrap(bytes)
    Try(decoder.decode(byteByffer).toString)
  }
  private def decode(bytes: Array[Byte], charset: JavaCharset): String =
    new String(bytes, charset)

  def apply(token: String): BasicCredentials = {
    val bytes = Base64.getDecoder.decode(token)
    val (userPass, charset) = decode(bytes, utf8CharsetDecoder)
      .fold(_ => (decode(bytes, fallbackCharset), fallbackCharset), up => (up, utf8Charset))
    userPass.indexOf(':') match {
      case -1 => apply(userPass, "", charset)
      case ix => apply(userPass.substring(0, ix), userPass.substring(ix + 1), charset)
    }
  }

  def unapply(creds: Credentials): Option[(String, String)] =
    creds match {
      case Credentials.Token(AuthScheme.Basic, token) =>
        val basicCredentials = BasicCredentials(token)
        Some((basicCredentials.username, basicCredentials.password))
      case _ =>
        None
    }
}
