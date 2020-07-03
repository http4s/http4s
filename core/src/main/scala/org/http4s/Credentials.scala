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
import java.nio.charset.StandardCharsets
import java.util.Base64

import org.http4s.util.{Renderable, Writer}
import java.nio.charset.CharsetDecoder
import java.nio.ByteBuffer
import scala.util.Try
import java.nio.charset.{Charset => JavaCharset}

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
      params.tail.foreach {
        case (k, v) =>
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
        params: (String, String)*): AuthParams =
      apply(authScheme, NonEmptyList(param, params.toList))
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
  private val utf8Charset = StandardCharsets.UTF_8
  private val utf8CharsetDecoder = utf8Charset.newDecoder
  private def decode(bytes: Array[Byte], decoder: CharsetDecoder): Try[String] = {
    val byteByffer = ByteBuffer.wrap(bytes)
    Try(decoder.decode(byteByffer).toString)
  }
  private def decode(bytes: Array[Byte], charset: JavaCharset): String =
    new String(bytes, charset)

  def apply(token: String): BasicCredentials = {
    val bytes = Base64.getDecoder.decode(token)
    val userPass: String = decode(bytes, utf8CharsetDecoder)
      .fold(_ => decode(bytes, StandardCharsets.ISO_8859_1), identity)
    userPass.indexOf(':') match {
      case -1 => apply(userPass, "")
      case ix => apply(userPass.substring(0, ix), userPass.substring(ix + 1))
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
