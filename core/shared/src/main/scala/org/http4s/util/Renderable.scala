package org.http4s.util

import cats.data.NonEmptyList
import java.time.{Instant, ZoneId}
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
    * @param writer [[Writer]] to render to
    * @param t object to render
    * @return the same [[Writer]] provided
    */
  def render(writer: Writer, t: T): writer.type
}

object Renderer {
  def renderString[T: Renderer](t: T): String = new StringWriter().append(t).result

  implicit val RFC7231InstantRenderer: Renderer[Instant] = new Renderer[Instant] {

    private val dateFormat =
      DateTimeFormatter
        .ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz")
        .withLocale(Locale.US)
        .withZone(ZoneId.of("GMT"))

    override def render(writer: Writer, t: Instant): writer.type =
      writer << dateFormat.format(t)

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

  implicit def eitherRenderer[A, B](
      implicit ra: Renderer[A],
      rb: Renderer[B]): Renderer[Either[A, B]] = new Renderer[Either[A, B]] {
    override def render(writer: Writer, e: Either[A, B]): writer.type =
      e match {
        case Left(a) => ra.render(writer, a)
        case Right(b) => rb.render(writer, b)
      }
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
  val HeaderValueDQuote = BitSet("\\\"".map(_.toInt): _*)
}

/** Efficiently accumulate [[Renderable]] representations */
trait Writer {
  def append(s: String): this.type
  def append(ci: CaseInsensitiveString): this.type = append(ci.toString)
  def append(char: Char): this.type = append(char.toString)
  def append(float: Float): this.type = append(float.toString)
  def append(double: Double): this.type = append(double.toString)
  def append(int: Int): this.type = append(int.toString)
  def append(long: Long): this.type = append(long.toString)

  def append[T](r: T)(implicit R: Renderer[T]): this.type = R.render(this, r)

  def quote(
      s: String,
      escapedChars: BitSet = Writer.HeaderValueDQuote,
      escapeChar: Char = '\\'): this.type = {
    this << '"'

    @tailrec
    def go(i: Int): Unit = if (i < s.length) {
      val c = s.charAt(i)
      if (escapedChars.contains(c.toInt)) this << escapeChar
      this << c
      go(i + 1)
    }

    go(0)
    this << '"'
  }

  def addStrings(
      s: Seq[String],
      sep: String = "",
      start: String = "",
      end: String = ""): this.type = {
    append(start)
    if (s.nonEmpty) {
      append(s.head)
      s.tail.foreach(s => append(sep).append(s))
    }
    append(end)
  }

  def addStringNel(
      s: NonEmptyList[String],
      sep: String = "",
      start: String = "",
      end: String = ""): this.type = {
    append(start)
    append(s.head)
    s.tail.foreach(s => append(sep).append(s))
    append(end)
  }

  def addSeq[T: Renderer](
      s: Seq[T],
      sep: String = "",
      start: String = "",
      end: String = ""): this.type = {
    append(start)
    if (s.nonEmpty) {
      append(s.head)
      s.tail.foreach(s => append(s).append(sep))
    }
    append(end)
  }

  final def <<(s: String): this.type = append(s)
  final def <<#(s: String): this.type = quote(s)
  final def <<(s: CaseInsensitiveString): this.type = append(s)
  final def <<(char: Char): this.type = append(char)
  final def <<(float: Float): this.type = append(float)
  final def <<(double: Double): this.type = append(double)
  final def <<(int: Int): this.type = append(int)
  final def <<(long: Long): this.type = append(long)
  final def <<[T: Renderer](r: T): this.type = append(r)

}

/** [[Writer]] that will result in a `String`
  * @param size initial buffer size of the underlying `StringBuilder`
  */
class StringWriter(size: Int = StringWriter.InitialCapacity) extends Writer {
  private val sb = new java.lang.StringBuilder(size)

  def append(s: String): this.type = { sb.append(s); this }
  override def append(char: Char): this.type = { sb.append(char); this }
  override def append(float: Float): this.type = { sb.append(float); this }
  override def append(double: Double): this.type = { sb.append(double); this }
  override def append(int: Int): this.type = { sb.append(int); this }
  override def append(long: Long): this.type = { sb.append(long); this }

  def result: String = sb.toString
}

object StringWriter {
  private val InitialCapacity = 64
}
