/*
 * Copyright 2015 http4s.org
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
package bench

import cats.syntax.all._
import org.http4s.internal.CollectionCompat.CollectionConverters._
import org.openjdk.jmh.annotations._

import java.nio.charset.UnsupportedCharsetException
import java.nio.charset.{Charset => NioCharset}
import java.util.HashMap
import java.util.Locale
import java.util.concurrent.TimeUnit
import scala.collection.mutable

@Fork(2)
@Threads(8)
@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
class NioCharsetBench {
  private val javaCache: HashMap[String, NioCharset] = {
    val map = new HashMap[String, NioCharset]
    for {
      cs <- NioCharset.availableCharsets.values.asScala
      name <- cs.name :: cs.aliases.asScala.toList
    } map.put(name.toLowerCase(Locale.ROOT), cs)
    map
  }

  private val scalaCache: mutable.Map[String, NioCharset] =
    mutable.Map(javaCache.asScala.toSeq: _*)

  def javaCached(name: String): Either[UnsupportedCharsetException, NioCharset] =
    javaCache.get(name.toLowerCase(Locale.ROOT)) match {
      case null => Left(new UnsupportedCharsetException(name))
      case cs => Right(cs)
    }

  def scalaCached(name: String): Either[UnsupportedCharsetException, NioCharset] =
    scalaCache.get(name.toLowerCase(Locale.ROOT)) match {
      case None => Left(new UnsupportedCharsetException(name))
      case Some(cs) => Right(cs)
    }

  @Benchmark
  def forNameValid: Either[Throwable, NioCharset] =
    Either.catchNonFatal(NioCharset.forName("utf-8"))

  @Benchmark
  def javaCachedValid: Either[Throwable, NioCharset] =
    javaCached("utf-8")

  @Benchmark
  def scalaCachedValid: Either[Throwable, NioCharset] =
    scalaCached("utf-8")

  @Benchmark
  def forNameInvalid: Either[Throwable, NioCharset] =
    Either.catchNonFatal(NioCharset.forName("ftu-8"))

  @Benchmark
  def javaCachedInvalid: Either[Throwable, NioCharset] =
    javaCached("ftu-8")

  @Benchmark
  def scalaCachedInvalid: Either[Throwable, NioCharset] =
    scalaCached("ftu-8")
}
