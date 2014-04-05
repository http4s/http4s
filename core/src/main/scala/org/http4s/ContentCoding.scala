package org.http4s

import org.http4s.util.{Resolvable, Writer, Renderable, CaseInsensitiveString}

final case class ContentCoding private (coding: CaseInsensitiveString, q: Q = Q.Unity)
                  extends QualityFactor with Renderable {

  def withQuality(q: Q): ContentCoding = copy(coding, q)
  def satisfies(encoding: ContentCoding) = encoding.satisfiedBy(this)
  def satisfiedBy(encoding: ContentCoding) = {
    (this.coding.toString == "*" || this.coding == encoding.coding) &&
    !(q.unacceptable || encoding.q.unacceptable)
  }

  def render[W <: Writer](writer: W) = {
    writer.append(coding.toString).append(q)
  }

  // We want the normal case class generated methods except copy
  private def copy(coding: CaseInsensitiveString = this.coding, q: Q = this.q) = new ContentCoding(coding, q)
}

object ContentCoding extends Resolvable[CaseInsensitiveString, ContentCoding] {
  protected def stringToRegistryKey(s: String): CaseInsensitiveString = s.ci

  protected def fromKey(k: CaseInsensitiveString): ContentCoding = new ContentCoding(k)

  def register(encoding: ContentCoding): ContentCoding = {
    register(encoding.coding, encoding)
    encoding
  }

  def register(value: String): ContentCoding = register(ContentCoding(value.ci))

  val `*`: ContentCoding = register("*")

  // http://www.iana.org/assignments/http-parameters/http-parameters.xml#http-parameters-1
  val compress       = register("compress")
  val deflate        = register("deflate")
  val exi            = register("exi")
  val gzip           = register("gzip")
  val identity       = register("identity")
  val `pack200-gzip` = register("pack200-gzip")

  // Legacy encodings defined by RFC2616 3.5.
  val `x-compress`   = register("x-compress".ci, compress)
  val `x-gzip`       = register("x-gzip".ci, gzip)
}
