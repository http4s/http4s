package org.http4s
package server
package middleware

import java.util.zip.{CRC32, Deflater}
import javax.xml.bind.DatatypeConverter

import fs2._
import fs2.Stream._
import fs2.compress._
import fs2.interop.cats._
import org.http4s.EntityBody
import org.http4s.headers._
import org.log4s.getLogger

object GZip {
  private[this] val logger = getLogger
  // TODO: It could be possible to look for Task.now type bodies, and change the Content-Length header after
  // TODO      zipping and buffering all the input. Just a thought.
  def apply(service: HttpService, bufferSize: Int = 32 * 1024, level: Int = Deflater.DEFAULT_COMPRESSION): HttpService = Service.lift {
    req: Request =>
      req.headers.get(`Accept-Encoding`) match {
        case Some(acceptEncoding) if acceptEncoding.satisfiedBy(ContentCoding.gzip)
                                  || acceptEncoding.satisfiedBy(ContentCoding.`x-gzip`) =>
          service.map {
            case resp: Response =>
              if (isZippable(resp)) {
                logger.trace("GZip middleware encoding content")
                // Need to add the Gzip header and trailer
                val gzipTrailer = trailer(resp.body)
                val b = chunk(header) ++
                  resp.body.through(deflate(
                    level = level,
                    nowrap = true,
                    bufferSize = bufferSize
                  )) ++ gzipTrailer
                resp.removeHeader(`Content-Length`)
                  .putHeaders(`Content-Encoding`(ContentCoding.gzip))
                  .copy(body = b)
              }
              else resp  // Don't touch it, Content-Encoding already set
            case Pass => Pass
          }.apply(req)

        case _ => service(req)
      }
  }

  private def isZippable(resp: Response): Boolean = {
    val contentType = resp.headers.get(`Content-Type`)
    resp.headers.get(`Content-Encoding`).isEmpty &&
      (contentType.isEmpty || contentType.get.mediaType.compressible ||
      (contentType.get.mediaType eq MediaType.`application/octet-stream`))
  }


  private val GZIP_MAGIC_NUMBER = 0x8b1f
  private val GZIP_LENGTH_MOD = Math.pow(2, 32).toLong

  private val header: Chunk[Byte] = Chunk.bytes(Array(
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
  )

  private def trailer(body: EntityBody): Stream[Task, Byte] =
    body.fold(Array[Byte]())((arr, byte) => arr :+ byte)
      .map { arr =>
        val crc = new CRC32()
        crc.update(arr)
        DatatypeConverter.parseHexBinary("%08x".format(arr.length % GZIP_LENGTH_MOD)) ++
          DatatypeConverter.parseHexBinary("%08x".format(crc.getValue()))
      }.flatMap(arr => Stream(arr.reverse:_*))
}
