/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Based on https://github.com/spray/spray/blob/v1.1-M7/spray-http/src/main/scala/spray/http/RangeUnit.scala
 * Copyright (C) 2011-2012 spray.io
 * Based on code copyright (C) 2010-2011 by the BlueEyes Web Framework Team
 */

package org.http4s

import org.http4s.util.{Renderable, Writer}

object RangeUnit {
  val Bytes = RangeUnit("bytes") // The only range-unit defined in rfc7233
  val None = new RangeUnit("none")
}

final case class RangeUnit(value: String) extends Renderable {
  override def toString: String = value
  def render(writer: Writer): writer.type = writer.append(value)
}
