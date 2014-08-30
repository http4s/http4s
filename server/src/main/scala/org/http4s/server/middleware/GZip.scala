package org.http4s
package server
package middleware

import java.util.zip.Deflater

import org.http4s.Header.{`Content-Type`, `Content-Length`, `Content-Encoding`, `Accept-Encoding`}

import scalaz.stream.Process._
import scalaz.concurrent.Task

import scodec.bits.ByteVector

import com.typesafe.scalalogging.slf4j.StrictLogging

object GZip extends StrictLogging {
  
  // TODO: It could be possible to look for Task.now type bodies, and change the Content-Length header after
  // TODO      zipping and buffering all the input. Just a thought.
  def apply(route: HttpService, buffersize: Int = 512, level: Int = Deflater.DEFAULT_COMPRESSION): HttpService = { req =>
      //Header.`Accept-Encoding` req.prelude.headers
      route(req).map{ t =>
      req.headers.get(`Accept-Encoding`).fold(t) { h =>
        if (h.satisfiedBy(ContentCoding.gzip) || h.satisfiedBy(ContentCoding.`x-gzip`)) t.map { resp =>
          // Accepts encoding. Make sure Content-Encoding is not set and transform body and add the header
          val contentType = resp.headers.get(`Content-Type`)
          if (resp.headers.get(`Content-Encoding`).isEmpty &&
            (contentType.isEmpty ||
              contentType.get.mediaType.compressible ||
              (contentType.get.mediaType eq MediaType.`application/octet-stream`))) {
            logger.trace("GZip middleware encoding content")
            // Need to add the Gzip header
            val b = emit(ByteVector.view(header)) ++
              resp.body.pipe(scalaz.stream.compress.deflate(level = level, nowrap = true))

            resp.removeHeader(`Content-Length`)
              .putHeaders(`Content-Encoding`(ContentCoding.gzip))
              .copy(body = b)
          }
          else resp // Don't touch it, Content-Encoding already set
        } else t
      }
    }
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
