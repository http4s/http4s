/*
 * Derived from https://github.com/spray/spray/blob/v1.1-M7/spray-http/src/main/scala/spray/http/HttpCredentials.scala
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

import java.nio.charset.StandardCharsets
import java.util.Base64

import org.http4s.util.{NonEmptyList, Renderable, Writer}

sealed abstract class Credentials extends Renderable {
  def authScheme: AuthScheme
}

object Credentials {
  final case class Token(authScheme: AuthScheme, token: String)
      extends Credentials {
    def render(writer: Writer): writer.type =
      writer << authScheme << ' ' << token
  }

  final case class AuthParams(authScheme: AuthScheme, params: NonEmptyList[(String, String)])
      extends Credentials {
    def render(writer: Writer): writer.type = {
      writer << authScheme
      writer << ' '
      var first = true
      params.foreach { case (k, v) =>
        if (first) first = false
        else writer.append(',')

        if (k.isEmpty) writer << '"'
        else writer<< k << '=' << '"'

        v.foreach {
          case '"' => writer << '\\' << '"'
          case '\\' => writer << '\\' << '\\'
          case c => writer << c
        }
        writer << '"'
      }
      writer
    }
  }

  object AuthParams {
    def apply(authScheme: AuthScheme, param: (String, String), params: (String, String)*): AuthParams =
      apply(authScheme, NonEmptyList(param, params:_*))
  }
}

final case class BasicCredentials(username: String, password: String) {
  lazy val token = {
    val userPass = username + ':' + password
    val bytes = userPass.getBytes(StandardCharsets.ISO_8859_1)
    Base64.getEncoder.encodeToString(bytes)
  }
}

object BasicCredentials {
  def apply(token: String): BasicCredentials = {
    val bytes = Base64.getDecoder.decode(token)
    val userPass = new String(bytes, StandardCharsets.ISO_8859_1)
    userPass.indexOf(':') match {
      case -1 => apply(userPass, "")
      case ix => apply(userPass.substring(0, ix), userPass.substring(ix + 1))
    }
  }

  def unapply(creds: Credentials): Option[BasicCredentials] =
    creds match {
      case Credentials.Token(AuthScheme.Basic, token) =>
        Some(BasicCredentials(token))
      case _ =>
        None
    }
}



