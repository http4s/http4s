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

import org.http4s.util.{Renderable, Writer}
import org.typelevel.ci._
import scala.concurrent.duration.Duration

sealed trait CacheDirective extends Product with Renderable {
  def name: CIString
  def value: String = name.toString
  override def toString: String = value
  def render(writer: Writer): writer.type = writer.append(value)
}

/** A registry of cache-directives, as listed in
  * http://www.iana.org/assignments/http-cache-directives/http-cache-directives.xhtml
  */
object CacheDirective {
  final case class MaxAge(deltaSeconds: Duration) extends CacheDirective {
    def name = MaxAge.name
    override def value: String = name.toString + "=" + deltaSeconds.toSeconds
  }
  object MaxAge {
    val name = ci"max-age"
  }

  final case class MaxStale(deltaSeconds: Option[Duration] = None) extends CacheDirective {
    def name = MaxStale.name
    override def value: String = name.toString + deltaSeconds.fold("")("=" + _.toSeconds)
  }
  object MaxStale {
    val name = ci"max-stale"
  }

  final case class MinFresh(deltaSeconds: Duration) extends CacheDirective {
    def name = MinFresh.name
    override def value: String = name.toString + "=" + deltaSeconds.toSeconds
  }
  object MinFresh {
    val name = ci"min-fresh"
  }

  case object MustRevalidate extends CacheDirective {
    val name = ci"must-revalidate"
  }

  final case class NoCache(fieldNames: List[CIString] = Nil) extends CacheDirective {
    def name = NoCache.name
    override def value: String =
      name.toString + (if (fieldNames.isEmpty) "" else fieldNames.mkString("=\"", ",", "\""))
  }
  object NoCache {
    val name = ci"no-cache"
  }

  case object NoStore extends CacheDirective {
    val name = ci"no-store"
  }

  case object NoTransform extends CacheDirective {
    val name = ci"no-transform"
  }

  case object OnlyIfCached extends CacheDirective {
    val name = ci"only-if-cached"
  }

  final case class Private(fieldNames: List[CIString] = Nil) extends CacheDirective {
    val name = Private.name
    override def value: String =
      name.toString + (if (fieldNames.isEmpty) "" else fieldNames.mkString("=\"", ",", "\""))
  }
  object Private {
    val name = ci"private"
  }

  case object ProxyRevalidate extends CacheDirective {
    val name = ci"proxy-revalidate"
  }

  case object Public extends CacheDirective {
    val name = ci"public"
  }

  final case class SMaxage(deltaSeconds: Duration) extends CacheDirective {
    def name = SMaxage.name
    override def value: String = name.toString + "=" + deltaSeconds.toSeconds
  }
  object SMaxage {
    val name = ci"s-maxage"
  }

  final case class StaleIfError(deltaSeconds: Duration) extends CacheDirective {
    def name = StaleIfError.name
    override def value: String = name.toString + "=" + deltaSeconds.toSeconds
  }
  object StaleIfError {
    val name = ci"stale-if-error"
  }

  final case class StaleWhileRevalidate(deltaSeconds: Duration) extends CacheDirective {
    def name = StaleWhileRevalidate.name
    override def value: String = name.toString + "=" + deltaSeconds.toSeconds
  }
  object StaleWhileRevalidate {
    val name = ci"stale-while-revalidate"
  }

  def apply(name: CIString, argument: Option[String] = None): CacheDirective =
    new CustomCacheDirective(name, argument)

  def apply(name: String, argument: Option[String]): CacheDirective =
    apply(CIString(name), argument)

  def apply(name: String): CacheDirective = apply(name, None)

  private final case class CustomCacheDirective(
      override val name: CIString,
      argument: Option[String] = None)
      extends CacheDirective {
    override def value: String = name.toString + argument.fold("")("=\"" + _ + '"')
  }
}
