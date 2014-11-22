package org.http4s
package json

import org.http4s.Header.`Content-Type`
import scodec.bits.ByteVector

import scalaz.concurrent.Task

trait JsonSupport[J] extends JsonDecodeSupport[J] with JsonEncodeSupport[J]

trait JsonDecodeSupport[J] {
  def decodeJson(body: EntityBody): Task[J]

  implicit def jsonEntityDecoder: EntityDecoder[J] = EntityDecoder[J](
    msg => decodeJson(msg.body), MediaType.`application/json`
  )
}

trait JsonEncodeSupport[J] {
  def encodeJson(json: J): EntityBody

  implicit def jsonWritable: Writable[J] = Writable.processWritable[ByteVector]
    .contramap[J](encodeJson)
    .withContentType(`Content-Type`(MediaType.`application/json`))
}
