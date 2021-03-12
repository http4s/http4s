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
  final case class maxAge(deltaSeconds: Duration) extends CacheDirective {
    def name = maxAge.name
    override def value: String = name.toString + "=" + deltaSeconds.toSeconds
  }
  object maxAge {
    val name = ci"max-age"
  }
  @deprecated("Renamed to maxAge", "0.22.0-M6")
  type `max-age` = maxAge
  @deprecated("Renamed to maxAge", "0.22.0-M6")
  val `max-age` = maxAge

  final case class maxStale(deltaSeconds: Option[Duration] = None) extends CacheDirective {
    def name = maxStale.name
    override def value: String = name.toString + deltaSeconds.fold("")("=" + _.toSeconds)
  }
  object maxStale {
    val name = ci"max-stale"
  }
  @deprecated("Renamed to maxStale", "0.22.0-M6")
  type `max-stale` = maxStale
  @deprecated("Renamed to maxStale", "0.22.0-M6")
  val `max-stale` = maxStale

  final case class minFresh(deltaSeconds: Duration) extends CacheDirective {
    def name = minFresh.name
    override def value: String = name.toString + "=" + deltaSeconds.toSeconds
  }
  object minFresh {
    val name = ci"min-fresh"
  }
  @deprecated("Renamed to minFresh", "0.22.0-M6")
  type `min-fresh` = minFresh
  @deprecated("Renamed to minFresh", "0.22.0-M6")
  val `min-fresh` = minFresh

  case object mustRevalidate extends CacheDirective {
    val name = ci"must-revalidate"
  }
  @deprecated("Renamed to mustRevalidate", "0.22.0-M6")
  val `must-revalidate` = mustRevalidate

  final case class noCache(fieldNames: List[CIString] = Nil) extends CacheDirective {
    def name = noCache.name
    override def value: String =
      name.toString + (if (fieldNames.isEmpty) "" else fieldNames.mkString("=\"", ",", "\""))
  }
  object noCache {
    val name = ci"no-cache"
  }
  @deprecated("Renamed to noCache", "0.22.0-M6")
  type `no-cache` = noCache
  @deprecated("Renamed to noCache", "0.22.0-M6")
  val `no-cache` = noCache

  case object noStore extends CacheDirective {
    val name = ci"no-store"
  }
  @deprecated("Renamed to noStore", "0.22.0-M6")
  val `no-store` = noStore

  case object noTransform extends CacheDirective {
    val name = ci"no-transform"
  }
  @deprecated("Renamed to noTransform", "0.22.0-M6")
  val `no-transform` = noTransform

  case object onlyIfCached extends CacheDirective {
    val name = ci"only-if-cached"
  }
  @deprecated("Renamed to onlyIfCached", "0.22.0-M6")
  val `only-if-cached` = onlyIfCached

  final case class `private`(fieldNames: List[CIString] = Nil) extends CacheDirective {
    val name = `private`.name
    override def value: String =
      name.toString + (if (fieldNames.isEmpty) "" else fieldNames.mkString("=\"", ",", "\""))
  }
  object `private` {
    val name = ci"private"
  }

  case object proxyRevalidate extends CacheDirective {
    val name = ci"proxy-revalidate"
  }
  @deprecated("Renamed to proxyRevalidate", "0.22.0-M6")
  val `proxy-revalidate` = proxyRevalidate

  case object public extends CacheDirective {
    val name = ci"public"
  }

  final case class sMaxage(deltaSeconds: Duration) extends CacheDirective {
    def name = sMaxage.name
    override def value: String = name.toString + "=" + deltaSeconds.toSeconds
  }
  object sMaxage {
    val name = ci"s-maxage"
  }
  @deprecated("Renamed to sMaxage", "0.22.0-M6")
  type `s-maxage` = sMaxage
  @deprecated("Renamed to sMaxage", "0.22.0-M6")
  val `s-maxage` = sMaxage

  final case class staleIfError(deltaSeconds: Duration) extends CacheDirective {
    def name = staleIfError.name
    override def value: String = name.toString + "=" + deltaSeconds.toSeconds
  }
  object staleIfError {
    val name = ci"stale-if-error"
  }
  @deprecated("Renamed to staleIfError", "0.22.0-M6")
  type `stale-if-error` = staleIfError
  @deprecated("Renamed to staleIfError", "0.22.0-M6")
  val `stale-if-error` = staleIfError

  final case class staleWhileRevalidate(deltaSeconds: Duration) extends CacheDirective {
    def name = staleWhileRevalidate.name
    override def value: String = name.toString + "=" + deltaSeconds.toSeconds
  }
  object staleWhileRevalidate {
    val name = ci"stale-while-revalidate"
  }
  @deprecated("Renamed to staleWhileRevalidate", "0.22.0-M6")
  type `stale-while-revalidate` = staleWhileRevalidate
  @deprecated("Renamed to staleWhileRevalidate", "0.22.0-M6")
  val `stale-while-revalidate` = staleWhileRevalidate

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
