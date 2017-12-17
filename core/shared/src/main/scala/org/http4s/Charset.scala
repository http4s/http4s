/*
 * Derived from https://github.com/spray/spray/blob/v1.1-M7/spray-http/src/main/scala/spray/http/HttpCharset.scala
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

import java.nio.charset.{StandardCharsets, Charset => NioCharset}
import java.util.{HashMap, Locale}
import org.http4s.util._
import scala.collection.JavaConverters._

final case class Charset private (nioCharset: NioCharset) extends Renderable {
  @deprecated("Use `Accept-Charset`.isSatisfiedBy(charset)", "0.16.1")
  def satisfies(charsetRange: CharsetRange): Boolean = charsetRange.isSatisfiedBy(this)

  def withQuality(q: QValue): CharsetRange.Atom = CharsetRange.Atom(this, q)
  def toRange: CharsetRange.Atom = withQuality(QValue.One)

  def render(writer: Writer): writer.type = writer << nioCharset.name
}

object Charset {
  val `US-ASCII` = Charset(StandardCharsets.US_ASCII)
  val `ISO-8859-1` = Charset(StandardCharsets.ISO_8859_1)
  val `UTF-8` = Charset(StandardCharsets.UTF_8)
  val `UTF-16` = Charset(StandardCharsets.UTF_16)
  val `UTF-16BE` = Charset(StandardCharsets.UTF_16BE)
  val `UTF-16LE` = Charset(StandardCharsets.UTF_16LE)

  // Charset.forName caches a whopping two values and then
  // synchronizes.  We can prevent this by pre-caching all the lookups
  // that will succeed.
  private val cache: HashMap[String, NioCharset] = {
    val map = new HashMap[String, NioCharset]
    for {
      cs <- NioCharset.availableCharsets.values.asScala
      name <- cs.name :: cs.aliases.asScala.toList
    } map.put(name.toLowerCase(Locale.ROOT), cs)
    map
  }

  def fromNioCharset(nioCharset: NioCharset): Charset = Charset(nioCharset)

  def fromString(name: String): ParseResult[Charset] =
    cache.get(name.toLowerCase(Locale.ROOT)) match {
      case nioCharset: NioCharset => Right(Charset.apply(nioCharset))
      case null => Left(ParseFailure("Invalid charset", s"$name is not a supported Charset"))
    }
}
