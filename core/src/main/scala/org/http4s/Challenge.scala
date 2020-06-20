/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Based on https://github.com/spray/spray/blob/v1.1-M7/spray-http/src/main/scala/spray/http/HttpChallenge.scala
 * Copyright (C) 2011-2012 spray.io
 * Based on code copyright (C) 2010-2011 by the BlueEyes Web Framework Team
 */

package org.http4s

import org.http4s.util.{Renderable, Writer}

final case class Challenge(scheme: String, realm: String, params: Map[String, String] = Map.empty)
    extends Renderable {
  lazy val value = renderString

  override def render(writer: Writer): writer.type = {
    writer.append(scheme).append(' ')
    writer.append("realm=\"").append(realm).append('"')
    params.foreach { case (k, v) => addPair(writer, k, v) }
    writer
  }

  @inline
  private def addPair(b: Writer, k: String, v: String): b.type =
    b.append(',').append(k).append("=\"").append(v).append('"')

  override def toString: String = value
}
