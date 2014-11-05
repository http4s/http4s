package org.http4s
package server
package middleware

import java.util.zip.Deflater

import org.http4s.Header.{`Content-Type`, `Content-Length`, `Content-Encoding`, `Accept-Encoding`}
import org.log4s.getLogger

import scalaz.stream.Process._
import scalaz.concurrent.Task

import scodec.bits.ByteVector

object GZip {
  private[this] val logger = getLogger
  
  // TODO: It could be possible to look for Task.now type bodies, and change the Content-Length header after
  // TODO      zipping and buffering all the input. Just a thought.
  def apply(service: HttpService, buffersize: Int = 512, level: Int = Deflater.DEFAULT_COMPRESSION): HttpService = {
    Service.lift { req: Request =>
      req.headers.get(`Accept-Encoding`) match {
        case Some(acceptEncoding) if acceptEncoding.satisfiedBy(ContentCoding.gzip)
                                  || acceptEncoding.satisfiedBy(ContentCoding.`x-gzip`) =>
          service.map { resp =>
            if (isZippable(resp)) {
              logger.trace("GZip middleware encoding content")
              // Need to add the Gzip header
              val b = emit(ByteVector.view(header)) ++
                        resp.body.pipe(scalaz.stream.compress.deflate(level = level, nowrap = true))

              resp.removeHeader(`Content-Length`)
                .putHeaders(`Content-Encoding`(ContentCoding.gzip))
                .copy(body = b)
            }
            else resp  // Don't touch it, Content-Encoding already set
          }(req)

        case None =>  service(req)
      }
    }
  }

  private def isZippable(resp: Response): Boolean = {
    val contentType = resp.headers.get(`Content-Type`)
    resp.headers.get(`Content-Encoding`).isEmpty &&
      (contentType.isEmpty || contentType.get.mediaType.compressible ||
      (contentType.get.mediaType eq MediaType.`application/octet-stream`))
  }



  private val GZIP_MAGIC_NUMBER = 0x8b1f
  private val TRAILER_LENGTH = 8

  private val header: Array[Byte] = Array(
    GZIP_MAGIC_NUMBER.toByte,           // Magic number (int16)
    (GZIP_MAGIC_NUMBER >> 8).toByte,    // Magic number  c
    Deflater.DEFLATED.toByte,           // Compression method
    0.toByte,                           // Flags
    0.toByte,                           // Modification time (int32)
    0.toByte,                           // Modification time  c
    0.toByte,                           // Modification time  c
    0.toByte,                           // Modification time  c
    0.toByte,                           // Extra flags
    0.toByte)                           // Operating system

}
