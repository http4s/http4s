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
  @deprecated("Renamed to MaxAge", "0.22.0-M6")
  type `max-age` = MaxAge
  @deprecated("Renamed to MaxAge", "0.22.0-M6")
  val `max-age` = MaxAge

  final case class MaxStale(deltaSeconds: Option[Duration] = None) extends CacheDirective {
    def name = MaxStale.name
    override def value: String = name.toString + deltaSeconds.fold("")("=" + _.toSeconds)
  }
  object MaxStale {
    val name = ci"max-stale"
  }
  @deprecated("Renamed to MaxStale", "0.22.0-M6")
  type `max-stale` = MaxStale
  @deprecated("Renamed to MaxStale", "0.22.0-M6")
  val `max-stale` = MaxStale

  final case class MinFresh(deltaSeconds: Duration) extends CacheDirective {
    def name = MinFresh.name
    override def value: String = name.toString + "=" + deltaSeconds.toSeconds
  }
  object MinFresh {
    val name = ci"min-fresh"
  }
  @deprecated("Renamed to MinFresh", "0.22.0-M6")
  type `min-fresh` = MinFresh
  @deprecated("Renamed to MinFresh", "0.22.0-M6")
  val `min-fresh` = MinFresh

  case object MustRevalidate extends CacheDirective {
    val name = ci"must-revalidate"
  }
  @deprecated("Renamed to MustRevalidate", "0.22.0-M6")
  val `must-revalidate` = MustRevalidate

  final case class NoCache(fieldNames: List[CIString] = Nil) extends CacheDirective {
    def name = NoCache.name
    override def value: String =
      name.toString + (if (fieldNames.isEmpty) "" else fieldNames.mkString("=\"", ",", "\""))
  }
  object NoCache {
    val name = ci"no-cache"
  }
  @deprecated("Renamed to NoCache", "0.22.0-M6")
  type `no-cache` = NoCache
  @deprecated("Renamed to NoCache", "0.22.0-M6")
  val `no-cache` = NoCache

  case object NoStore extends CacheDirective {
    val name = ci"no-store"
  }
  @deprecated("Renamed to NoStore", "0.22.0-M6")
  val `no-store` = NoStore

  case object NoTransform extends CacheDirective {
    val name = ci"no-transform"
  }
  @deprecated("Renamed to NoTransform", "0.22.0-M6")
  val `no-transform` = NoTransform

  case object OnlyIfCached extends CacheDirective {
    val name = ci"only-if-cached"
  }
  @deprecated("Renamed to OnlyIfCached", "0.22.0-M6")
  val `only-if-cached` = OnlyIfCached

  final case class Private(fieldNames: List[CIString] = Nil) extends CacheDirective {
    val name = Private.name
    override def value: String =
      name.toString + (if (fieldNames.isEmpty) "" else fieldNames.mkString("=\"", ",", "\""))
  }
  object Private {
    val name = ci"private"
  }
  @deprecated("Renamed to Private", "0.22.0-M6")
  type `private` = Private
  @deprecated("Renamed to Private", "0.22.0-M6")
  val `private` = Private

  case object ProxyRevalidate extends CacheDirective {
    val name = ci"proxy-revalidate"
  }
  @deprecated("Renamed to ProxyRevalidate", "0.22.0-M6")
  val `proxy-revalidate` = ProxyRevalidate

  case object Public extends CacheDirective {
    val name = ci"public"
  }
  @deprecated("Renamed to Public", "0.22.0-M6")
  val public = Public

  final case class SMaxage(deltaSeconds: Duration) extends CacheDirective {
    def name = SMaxage.name
    override def value: String = name.toString + "=" + deltaSeconds.toSeconds
  }
  object SMaxage {
    val name = ci"s-maxage"
  }
  @deprecated("Renamed to SMaxage", "0.22.0-M6")
  type `s-maxage` = SMaxage
  @deprecated("Renamed to SMaxage", "0.22.0-M6")
  val `s-maxage` = SMaxage

  final case class StaleIfError(deltaSeconds: Duration) extends CacheDirective {
    def name = StaleIfError.name
    override def value: String = name.toString + "=" + deltaSeconds.toSeconds
  }
  object StaleIfError {
    val name = ci"stale-if-error"
  }
  @deprecated("Renamed to StaleIfError", "0.22.0-M6")
  type `stale-if-error` = StaleIfError
  @deprecated("Renamed to StaleIfError", "0.22.0-M6")
  val `stale-if-error` = StaleIfError

  final case class StaleWhileRevalidate(deltaSeconds: Duration) extends CacheDirective {
    def name = StaleWhileRevalidate.name
    override def value: String = name.toString + "=" + deltaSeconds.toSeconds
  }
  object StaleWhileRevalidate {
    val name = ci"stale-while-revalidate"
  }
  @deprecated("Renamed to StaleWhileRevalidate", "0.22.0-M6")
  type `stale-while-revalidate` = StaleWhileRevalidate
  @deprecated("Renamed to StaleWhileRevalidate", "0.22.0-M6")
  val `stale-while-revalidate` = StaleWhileRevalidate

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
