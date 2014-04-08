package org.http4s

import org.http4s.util._
import org.http4s.util.string._

final case class TransferCoding private (coding: CaseInsensitiveString) extends Renderable {
  def render[W <: Writer](writer: W) = writer.append(coding.toString)
}

object TransferCoding extends Registry {
  type Key = CaseInsensitiveString
  type Value = TransferCoding

  implicit def fromKey(k: CaseInsensitiveString): TransferCoding = new TransferCoding(k)

  // http://www.iana.org/assignments/http-parameters/http-parameters.xml#http-parameters-2
  val chunked        = registerKey("chunked".ci)
  val compress       = registerKey("compress".ci)
  val deflate        = registerKey("deflate".ci)
  val gzip           = registerKey("gzip".ci)
  val identity       = registerKey("identity".ci)
}
