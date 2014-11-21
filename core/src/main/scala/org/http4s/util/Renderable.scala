package org.http4s.util

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
}

/** Mixin that makes a type compatible writable by a [[Writer]] without needing a [[Renderer]] instance */
trait Renderable extends Any {

  /** Base method for rendering this object efficiently */
  def render(writer: Writer): writer.type

  /** Generates a String rendering of this object */
  def renderString = Renderer.renderString(this)

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

/** Efficiently accumulate [[Renderable]] representations */
trait Writer {
  def append(s: String):                 this.type
  def append(ci: CaseInsensitiveString): this.type = append(ci.toString)
  def append(char: Char):                this.type = append(char.toString)
  def append(float: Float):              this.type = append(float.toString)
  def append(double: Double):            this.type = append(double.toString)
  def append(int: Int):                  this.type = append(int.toString)
  def append(long: Long):                this.type = append(long.toString)

  def append[T](r: T)(implicit R: Renderer[T]): this.type = R.render(this, r)

  def addStrings(s: Seq[String], sep: String = "", start: String = "", end: String = ""): this.type = {
    append(start)
    if (s.nonEmpty) {
      append(s.head)
      s.tail.foreach(s => append(s).append(sep))
    }
    append(end)
  }

  def addSeq[T: Renderer](s: Seq[T], sep: String = "", start: String = "", end: String = ""): this.type = {
    append(start)
    if (s.nonEmpty) {
      append(s.head)
      s.tail.foreach(s => append(s).append(sep))
    }
    append(end)
  }

  final def <<(s: String):                this.type = append(s)
  final def <<(s: CaseInsensitiveString): this.type = append(s)
  final def <<(char: Char):               this.type = append(char)
  final def <<(float: Float):             this.type = append(float)
  final def <<(double: Double):           this.type = append(double)
  final def <<(int: Int):                 this.type = append(int)
  final def <<(long: Long):               this.type = append(long)
  final def <<[T: Renderer](r: T):        this.type = append(r)

}

/** [[Writer]] that will result in a `String`
  * @param size initial buffer size of the underlying `StringBuilder`
  */
class StringWriter(size: Int = 64) extends Writer {
  private val sb = new java.lang.StringBuilder(size)

  def append(s: String) = { sb.append(s); this }
  override def append(char: Char) = { sb.append(char); this }
  override def append(float: Float) = { sb.append(float); this }
  override def append(double: Double) = { sb.append(double); this }
  override def append(int: Int) = { sb.append(int); this }
  override def append(long: Long) = { sb.append(long); this }

  def result(): String = sb.toString
}
