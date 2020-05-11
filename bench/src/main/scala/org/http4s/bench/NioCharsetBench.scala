package org.http4s
package bench

import cats.implicits._
import java.nio.charset.{UnsupportedCharsetException, Charset => NioCharset}
import java.util.{HashMap, Locale}
import java.util.concurrent.TimeUnit
import org.http4s.internal.CollectionCompat.CollectionConverters._
import org.openjdk.jmh.annotations._
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

  def javaCached(name: String) =
    javaCache.get(name.toLowerCase(Locale.ROOT)) match {
      case null => Left(new UnsupportedCharsetException(name))
      case cs => Right(cs)
    }

  def scalaCached(name: String) =
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
