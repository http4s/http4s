package org.http4s.util

trait Renderable {
  def render[W <: Writer](writer: W): writer.type

  override def toString: String = render(new StringWriter).result
}

trait ValueRenderable extends Renderable {

  def renderValue[W <: Writer](writer: W): writer.type

  override def render[W <: Writer](writer: W): writer.type = renderValue(writer)

  def value: String = renderValue(new StringWriter).result()
}

trait Writer {
  def append(s: String):                 this.type
  def append(ci: CaseInsensitiveString): this.type = append(ci.toString)
  def append(char: Char):                this.type = append(char.toString)
  def append(float: Float):              this.type = append(float.toString)
  def append(double: Double):            this.type = append(double.toString)
  def append(int: Int):                  this.type = append(int.toString)
  def append(long: Long):                this.type = append(long.toString)

  def append(r: Renderable): this.type = r.render(this)

  def addStrings(s: Seq[String], sep: String = "", start: String = "", end: String = ""): this.type = {
    append(start)
    if (!s.isEmpty) {
      append(s.head)
      s.tail.foreach(s => append(s).append(sep))
    }
    append(end)
  }

  def addSeq(s: Seq[Renderable], sep: String = "", start: String = "", end: String = ""): this.type = {
    append(start)
    if (!s.isEmpty) {
      append(s.head)
      s.tail.foreach(s => append(s).append(sep))
    }
    append(end)
  }

  final def ~(s: String):                this.type = append(s)
  final def ~(s: CaseInsensitiveString): this.type = append(s)
  final def ~(char: Char):               this.type = append(char)
  final def ~(float: Float):             this.type = append(float)
  final def ~(double: Double):           this.type = append(double)
  final def ~(int: Int):                 this.type = append(int)
  final def ~(long: Long):               this.type = append(long)
  final def ~(r: Renderable):            this.type = append(r)
}

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
