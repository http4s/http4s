package org.http4s

import org.http4s.util.{Resolvable, Writer, Renderable, CaseInsensitiveString}

final case class TransferCoding private (coding: CaseInsensitiveString) extends Renderable {
  def render[W <: Writer](writer: W) = writer.append(coding.toString)
}

object TransferCoding extends Resolvable[CaseInsensitiveString, TransferCoding] {
  protected def stringToRegistryKey(s: String): CaseInsensitiveString = s.ci

  protected def fromKey(k: CaseInsensitiveString): TransferCoding = new TransferCoding(k)

  def register(encoding: TransferCoding): TransferCoding = {
    register(encoding.coding, encoding)
    encoding
  }

  def register(value: String): TransferCoding = TransferCoding(value.ci)

  // http://www.iana.org/assignments/http-parameters/http-parameters.xml#http-parameters-2
  val chunked        = register("chunked")
  val compress       = register("compress")
  val deflate        = register("deflate")
  val gzip           = register("gzip")
  val identity       = register("identity")
}
