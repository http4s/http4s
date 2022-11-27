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

package org.http4s.util

import cats.data.NonEmptyList
import org.http4s.Header
import org.http4s.internal.CharPredicate
import org.typelevel.ci.CIString

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import scala.annotation.tailrec
import scala.collection.immutable.BitSet
import scala.concurrent.duration.FiniteDuration

/** A type class that describes how to efficiently render a type
  * @tparam T the type which will be rendered
  */
trait Renderer[T] {

  /** Renders the object to the writer
    * @param writer [[org.http4s.util.Writer]] to render to
    * @param t object to render
    * @return the same [[org.http4s.util.Writer]] provided
    */
  def render(writer: Writer, t: T): writer.type
}

object Renderer {
  @inline def apply[A](implicit ev: Renderer[A]): Renderer[A] = ev

  def renderString[T: Renderer](t: T): String = new StringWriter().append(t).result

  implicit lazy val RFC7231InstantRenderer: Renderer[Instant] = new Renderer[Instant] {
    private val dateFormat =
      DateTimeFormatter
        .ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz")
        .withLocale(Locale.US)
        .withZone(ZoneId.of("GMT"))

    override def render(writer: Writer, t: Instant): writer.type =
      writer << dateFormat.format(t)
  }

  implicit val stringRenderer: Renderer[String] = new Renderer[String] {
    override def render(writer: Writer, string: String): writer.type =
      writer << string
  }

  // Render a finite duration in seconds
  implicit val finiteDurationRenderer: Renderer[FiniteDuration] = new Renderer[FiniteDuration] {
    override def render(writer: Writer, d: FiniteDuration): writer.type =
      writer << d.toSeconds.toString
  }

  // Render a long value, e.g. on the Age header
  implicit val longRenderer: Renderer[Long] = new Renderer[Long] {
    override def render(writer: Writer, d: Long): writer.type =
      writer << d.toString
  }

  implicit def eitherRenderer[A, B](implicit
      ra: Renderer[A],
      rb: Renderer[B],
  ): Renderer[Either[A, B]] =
    new Renderer[Either[A, B]] {
      override def render(writer: Writer, e: Either[A, B]): writer.type =
        e match {
          case Left(a) => ra.render(writer, a)
          case Right(b) => rb.render(writer, b)
        }
    }

  implicit val ciStringRenderer: Renderer[CIString] = new Renderer[CIString] {
    override def render(writer: Writer, ciString: CIString): writer.type =
      writer << ciString
  }

  implicit def nelRenderer[T: Renderer]: Renderer[NonEmptyList[T]] =
    new Renderer[NonEmptyList[T]] {
      override def render(writer: Writer, values: NonEmptyList[T]): writer.type =
        writer.addNel(values)
    }

  implicit def listRenderer[T: Renderer]: Renderer[List[T]] =
    new Renderer[List[T]] {
      override def render(writer: Writer, values: List[T]): writer.type =
        writer.addList(values)
    }

  implicit def setRenderer[T: Renderer]: Renderer[Set[T]] =
    new Renderer[Set[T]] {
      override def render(writer: Writer, values: Set[T]): writer.type =
        writer.addSet(values)
    }

  implicit def headerSelectRenderer[A](implicit select: Header.Select[A]): Renderer[A] =
    new Renderer[A] {
      override def render(writer: Writer, t: A): writer.type = writer << select.toRaw1(t)
    }
}

/** Mixin that makes a type writable by a [[Writer]] without needing a [[Renderer]] instance */
trait Renderable extends Any {

  /** Base method for rendering this object efficiently */
  def render(writer: Writer): writer.type

  /** Generates a String rendering of this object */
  def renderString: String = Renderer.renderString(this)

  override def toString: String = renderString
}

object Renderable {
  implicit def renderableInst[T <: Renderable]: Renderer[T] =
    genericInstance.asInstanceOf[Renderer[T]]

  // Cache the Renderable because GC pauses suck
  private val genericInstance = new Renderer[Renderable] {
    override def render(writer: Writer, t: Renderable): writer.type =
      t.render(writer)
  }
}

object Writer {
  val HeaderValueDQuote: BitSet = BitSet("\\\"".map(_.toInt): _*)
}

/** Efficiently accumulate [[Renderable]] representations */
trait Writer { self =>
  def append(s: String): this.type
  def append(ci: CIString): this.type = append(ci.toString)
  def append(char: Char): this.type = append(char.toString)
  def append(float: Float): this.type = append(float.toString)
  def append(double: Double): this.type = append(double.toString)
  def append(int: Int): this.type = append(int.toString)
  def append(long: Long): this.type = append(long.toString)

  def append[T](r: T)(implicit R: Renderer[T]): this.type = R.render(this, r)

  def quote(
      s: String,
      escapedChars: BitSet = Writer.HeaderValueDQuote,
      escapeChar: Char = '\\',
  ): this.type = {
    this << '"'

    @tailrec
    def go(i: Int): Unit =
      if (i < s.length) {
        val c = s.charAt(i)
        if (escapedChars.contains(c.toInt)) this << escapeChar
        this << c
        go(i + 1)
      }

    go(0)
    this << '"'
  }
  // Adapted from https://github.com/akka/akka-http/blob/b071bd67547714bd8bed2ccd8170fbbc6c2dbd77/akka-http-core/src/main/scala/akka/http/impl/util/Rendering.scala#L219-L229
  def eligibleOnly(s: String, keep: CharPredicate, placeholder: Char): this.type = {
    @tailrec def rec(ix: Int = 0): this.type =
      if (ix < s.length) {
        val c = s.charAt(ix)
        if (keep(c)) this << c
        else this << placeholder
        rec(ix + 1)
      } else this
    rec()
  }

  def addStrings(
      s: collection.Seq[String],
      sep: String = "",
      start: String = "",
      end: String = "",
  ): this.type = {
    append(start)
    if (s.nonEmpty) {
      append(s.head)
      s.tail.foreach(s => append(sep).append(s))
    }
    append(end)
  }

  def addList[T: Renderer](
      s: List[T],
      sep: String = ", ",
      start: String = "",
      end: String = "",
  ): this.type =
    s match {
      case Nil =>
        append(start)
        append(end)
      case x :: list =>
        append(start)
        append(x)
        list.foreach(s => append(sep).append(s))
        append(end)
    }

  def addNel[T: Renderer](
      s: NonEmptyList[T],
      sep: String = ", ",
      start: String = "",
      end: String = "",
  ): this.type = {
    append(start)
    append(s.head)
    s.tail.foreach(s => append(sep).append(s))
    append(end)
  }

  def addSet[T: Renderer](
      s: collection.Set[T],
      sep: String = ", ",
      start: String = "",
      end: String = "",
  ): this.type = {
    append(start)
    if (s.nonEmpty) {
      append(s.head)
      s.tail.foreach(s => append(sep).append(s))
    }
    append(end)
  }

  final def <<(s: String): this.type = append(s)
  final def <<#(s: String): this.type = quote(s)
  final def <<(s: CIString): this.type = append(s)
  final def <<(char: Char): this.type = append(char)
  final def <<(float: Float): this.type = append(float)
  final def <<(double: Double): this.type = append(double)
  final def <<(int: Int): this.type = append(int)
  final def <<(long: Long): this.type = append(long)
  final def <<[T: Renderer](r: T): this.type = append(r)

  def sanitize(f: Writer => Writer): this.type = {
    val w = new Writer {
      def append(s: String): this.type = {
        s.foreach(append(_))
        this
      }
      override def append(c: Char): this.type = {
        if (c == 0x0.toChar || c == '\r' || c == '\n')
          self.append(' ')
        else
          self.append(c)
        this
      }
    }
    f(w)
    this
  }
}

/** [[Writer]] that will result in a `String`
  * @param size initial buffer size of the underlying `StringBuilder`
  */
class StringWriter(size: Int = StringWriter.InitialCapacity) extends Writer { self =>
  private val sb = new java.lang.StringBuilder(size)

  def append(s: String): this.type = { sb.append(s); this }
  override def append(char: Char): this.type = { sb.append(char); this }
  override def append(float: Float): this.type = { sb.append(float); this }
  override def append(double: Double): this.type = { sb.append(double); this }
  override def append(int: Int): this.type = { sb.append(int); this }
  override def append(long: Long): this.type = { sb.append(long); this }

  override def sanitize(f: Writer => Writer): this.type = {
    val w = new Writer {
      def append(s: String): this.type = {
        val start = sb.length
        self.append(s)
        for (i <- start until sb.length) {
          val c = sb.charAt(i)
          if (c == 0x0.toChar || c == '\r' || c == '\n') {
            sb.setCharAt(i, ' ')
          }
        }
        this
      }
      override def append(c: Char): this.type = {
        if (c == 0x0.toChar || c == '\r' || c == '\n')
          self.append(' ')
        else
          self.append(c)
        this
      }
    }
    f(w)
    this
  }

  def result: String = sb.toString
}

object StringWriter {
  private val InitialCapacity = 64
}

private[http4s] class HeaderLengthCountingWriter extends Writer {
  var length: Long = 0L

  def append(s: String): this.type = {
    // Assumption: 1 byte per character. Only US-ASCII is supported.
    length = length + s.length
    this
  }

  override def sanitize(f: Writer => Writer): this.type = {
    f(this)
    this
  }
}
