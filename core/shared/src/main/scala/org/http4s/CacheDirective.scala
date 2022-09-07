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
 * Based on https://github.com/spray/spray/blob/v1.1-M7/spray-http/src/main/scala/spray/http/CacheDirective.scala
 * Copyright (C) 2011-2012 spray.io
 * Based on code copyright (C) 2010-2011 by the BlueEyes Web Framework Team
 */

package org.http4s

import org.http4s.util.Renderable
import org.http4s.util.Writer
import org.typelevel.ci.CIString

import scala.concurrent.duration.Duration

sealed trait CacheDirective extends Product with Renderable {
  val name: CIString = CIString(productPrefix.replace("$minus", "-"))
  def value: String = name.toString
  override def toString: String = value
  def render(writer: Writer): writer.type = writer.append(value)
}

/** A registry of cache-directives, as listed in
  * http://www.iana.org/assignments/http-cache-directives/http-cache-directives.xhtml
  */
object CacheDirective {
  final case class `max-age`(deltaSeconds: Duration) extends CacheDirective {
    override def value: String = name.toString + "=" + deltaSeconds.toSeconds
  }

  final case class `max-stale`(deltaSeconds: Option[Duration] = None) extends CacheDirective {
    override def value: String = name.toString + deltaSeconds.fold("")("=" + _.toSeconds)
  }

  final case class `min-fresh`(deltaSeconds: Duration) extends CacheDirective {
    override def value: String = name.toString + "=" + deltaSeconds.toSeconds
  }

  case object `must-revalidate` extends CacheDirective

  final case class `no-cache`(fieldNames: List[CIString] = Nil) extends CacheDirective {
    override def value: String =
      name.toString + (if (fieldNames.isEmpty) "" else fieldNames.mkString("=\"", ",", "\""))
  }

  case object `no-store` extends CacheDirective

  case object `no-transform` extends CacheDirective

  case object `only-if-cached` extends CacheDirective

  final case class `private`(fieldNames: List[CIString] = Nil) extends CacheDirective {
    override def value: String =
      name.toString + (if (fieldNames.isEmpty) "" else fieldNames.mkString("=\"", ",", "\""))
  }

  case object `proxy-revalidate` extends CacheDirective

  case object public extends CacheDirective

  final case class `s-maxage`(deltaSeconds: Duration) extends CacheDirective {
    override def value: String = name.toString + "=" + deltaSeconds.toSeconds
  }

  final case class `stale-if-error`(deltaSeconds: Duration) extends CacheDirective {
    override def value: String = name.toString + "=" + deltaSeconds.toSeconds
  }

  final case class `stale-while-revalidate`(deltaSeconds: Duration) extends CacheDirective {
    override def value: String = name.toString + "=" + deltaSeconds.toSeconds
  }

  def apply(name: CIString, argument: Option[String] = None): CacheDirective =
    new CustomCacheDirective(name, argument)

  def apply(name: String, argument: Option[String]): CacheDirective =
    apply(CIString(name), argument)

  def apply(name: String): CacheDirective = apply(name, None)

  private final case class CustomCacheDirective(
      override val name: CIString,
      argument: Option[String] = None,
  ) extends CacheDirective {
    override def value: String = name.toString + argument.fold("")("=\"" + _ + '"')
  }
}
