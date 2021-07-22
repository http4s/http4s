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

package org.http4s

import cats._
import java.nio.charset.{StandardCharsets, Charset => NioCharset}
import java.util.{HashMap, Locale}
import org.http4s.internal.CollectionCompat.CollectionConverters._
import org.http4s.util._

final case class Charset private (nioCharset: NioCharset) extends Renderable {
  def withQuality(q: QValue): CharsetRange.Atom = CharsetRange.Atom(this, q)
  def toRange: CharsetRange.Atom = withQuality(QValue.One)

  def render(writer: Writer): writer.type = writer << nioCharset.name
}

object Charset extends CharsetCompanionPlatform {

  implicit val catsInstancesForHttp4sCharset: Hash[Charset] with Order[Charset] =
    new Hash[Charset] with Order[Charset] {
      override def hash(x: Charset): Int =
        x.hashCode

      override def compare(x: Charset, y: Charset): Int =
        // Using Comparable
        x.nioCharset.compareTo(y.nioCharset)
    }

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
      cs <- availableCharsets
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
