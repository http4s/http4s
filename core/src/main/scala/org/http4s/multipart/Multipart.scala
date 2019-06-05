package org.http4s
package multipart

import cats.implicits._
import org.http4s.headers._

final case class Multipart[F[_]](parts: Vector[Part[F]], boundary: Boundary = Boundary.create) {
  def headers: Headers =
    Headers(
      List(
        contentLength
          .flatMap(len => `Content-Length`.fromLong(len).toOption)
          .getOrElse(`Transfer-Encoding`(TransferCoding.chunked)),
        `Content-Type`(MediaType.multipartType("form-data", Some(boundary.value)))
      )
    )

  def contentLength: Option[Long] = {
    val newlineLength = 2L
    parts.traverse(part => part.fullLength).map { lengths =>
      val partsLength = lengths.combineAll
      val numberOfParts = lengths.size
      val boundariesLength = (numberOfParts + 1) * Boundary.lengthInBytes
      val boundariesNewLinesLength = numberOfParts * newlineLength

      partsLength + boundariesLength + boundariesNewLinesLength
    }
  }
}
