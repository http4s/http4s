package org.http4s

import org.http4s.util._
import string._

final case class ContentCoding (coding: CaseInsensitiveString, q: Q = Q.Unity) extends QualityFactor with Renderable {

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
  private def copy(coding: CaseInsensitiveString = this.coding, q: Q = this.q) = ContentCoding(coding, q)
}

object ContentCoding extends Registry {
  type Key = CaseInsensitiveString
  type Value = ContentCoding

  implicit def fromKey(k: CaseInsensitiveString): ContentCoding = ContentCoding(k)

  implicit def fromValue(v: ContentCoding): CaseInsensitiveString = v.coding

  val `*`: ContentCoding = registerKey("*".ci)

  // http://www.iana.org/assignments/http-parameters/http-parameters.xml#http-parameters-1
  val compress       = registerKey("compress".ci)
  val deflate        = registerKey("deflate".ci)
  val exi            = registerKey("exi".ci)
  val gzip           = registerKey("gzip".ci)
  val identity       = registerKey("identity".ci)
  val `pack200-gzip` = registerKey("pack200-gzip".ci)

  // Legacy encodings defined by RFC2616 3.5.
  val `x-compress`   = register("x-compress".ci, compress)
  val `x-gzip`       = register("x-gzip".ci, gzip)
}
