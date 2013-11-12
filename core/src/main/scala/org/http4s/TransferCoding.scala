package org.http4s

final case class TransferCoding(value: CiString)

object TransferCoding extends ObjectRegistry[CiString, TransferCoding] {
  def register(encoding: TransferCoding): TransferCoding = {
    register(encoding.value, encoding)
    encoding
  }

  def register(value: String): TransferCoding = TransferCoding(value.lowercaseEn)

  // http://www.iana.org/assignments/http-parameters/http-parameters.xml#http-parameters-2
  val chunked        = register("chunked")
  val compress       = register("compress")
  val deflate        = register("deflate")
  val gzip           = register("gzip")
  val identity       = register("identity")
}
