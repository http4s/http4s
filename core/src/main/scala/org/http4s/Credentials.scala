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

import org.http4s.util.{Renderable, Writer}

sealed abstract class Credentials extends Renderable {
  def authScheme: AuthScheme
  def value: String
}

case class BasicCredentials(username: String, password: String) extends Credentials {
  val authScheme = AuthScheme.Basic

  override lazy val value = {
    val userPass = username + ':' + password
    val bytes = userPass.getBytes(StandardCharsets.ISO_8859_1)
    val cookie = Base64.getEncoder.encodeToString(bytes)
    "Basic " + cookie
  }

  override def render(writer: Writer): writer.type = writer.append(value)
}

object BasicCredentials {
  def apply(credentials: String): BasicCredentials = {
    val bytes = Base64.getDecoder.decode(credentials)
    val userPass = new String(bytes, StandardCharsets.ISO_8859_1)
    userPass.indexOf(':') match {
      case -1 => apply(userPass, "")
      case ix => apply(userPass.substring(0, ix), userPass.substring(ix + 1))
    }
  }
}


case class OAuth2BearerToken(token: String) extends Credentials {
  val authScheme = AuthScheme.Bearer

  override def value = renderString

  override def render(writer: Writer): writer.type = writer.append("Bearer ").append(token)
}


case class GenericCredentials(authScheme: AuthScheme, params: Map[String, String]) extends Credentials {
  override lazy val value = renderString

  override def render(writer: Writer): writer.type = {
    writer << authScheme
    if (params.nonEmpty) {
      writer << ' '
      formatParams(writer)
    }
    writer
  }

  private def formatParams(sb: Writer): Unit = {
    var first = true
    params.foreach { case (k, v) =>
      if (first) first = false
      else sb.append(',')

      if (k.isEmpty) sb << '"'
      else sb<< k << '=' << '"'

      v.foreach {
        case '"' => sb << '\\' << '"'
        case '\\' => sb << '\\' << '\\'
        case c => sb << c
      }
      sb << '"'
    }
  }
}

