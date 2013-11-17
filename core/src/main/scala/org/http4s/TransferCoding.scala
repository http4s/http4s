package org.http4s

import org.http4s.util.{Registry, CaseInsensitiveString}

final case class TransferCoding(value: CaseInsensitiveString)

object TransferCoding extends Registry[CaseInsensitiveString, TransferCoding] {
  def register(encoding: TransferCoding): TransferCoding = {
    register(encoding.value, encoding)
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
