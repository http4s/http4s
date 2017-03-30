package org.http4s
package bench

import java.nio.charset.{Charset => NioCharset, UnsupportedCharsetException}
import java.util.{HashMap, Locale}
import java.util.concurrent.TimeUnit
import scala.collection.mutable
import scala.collection.JavaConverters._
import scala.util.Try
import scalaz.\/

import org.openjdk.jmh.annotations._

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
    } {
      map.put(name.toLowerCase(Locale.ROOT), cs)
    }
    map
  }

  private val scalaCache: mutable.Map[String, NioCharset] =
    mutable.Map(javaCache.asScala.toSeq: _*)

  def javaCached(name: String) =
    javaCache.get(name.toLowerCase(Locale.ROOT)) match {
      case null => \/.left(new UnsupportedCharsetException(name))
      case cs => \/.right(cs)
    }

  def scalaCached(name: String) =
    scalaCache.get(name.toLowerCase(Locale.ROOT)) match {
      case None => \/.left(new UnsupportedCharsetException(name))
      case Some(cs) => \/.right(cs)
    }

  @Benchmark
  def forNameValid: Throwable \/ NioCharset =
    \/.fromTryCatchNonFatal(NioCharset.forName("utf-8"))

  @Benchmark
  def javaCachedValid: Throwable \/ NioCharset =
    javaCached("utf-8")

  @Benchmark
  def scalaCachedValid: Throwable \/ NioCharset =
    scalaCached("utf-8")

  @Benchmark
  def forNameInvalid: Throwable \/ NioCharset =
    \/.fromTryCatchNonFatal(NioCharset.forName("ftu-8"))

  @Benchmark
  def javaCachedInvalid: Throwable \/ NioCharset =
    javaCached("ftu-8")

  @Benchmark
  def scalaCachedInvalid: Throwable \/ NioCharset =
    scalaCached("ftu-8")
}
