package org.http4s
package server
package middleware

import java.nio.{ByteBuffer, ByteOrder}
import java.util.zip.{CRC32, Deflater}
import org.http4s.headers.{`Content-Type`, `Content-Length`, `Content-Encoding`, `Accept-Encoding`}
import org.log4s.getLogger
import scala.annotation.tailrec
import scalaz.stream.{ Process0, Process1 }
import scalaz.stream.Process._
import scalaz.concurrent.Task
import scalaz.Kleisli.kleisli
import scodec.bits.ByteVector

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
                val b = resp.body
                  .pipe(gzip(
                    level = level,
                    nowrap = true,
                    bufferSize = bufferSize
                  ))
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

  private[http4s] def gzip(level: Int = Deflater.DEFAULT_COMPRESSION,
              nowrap: Boolean = false,
              bufferSize: Int = 1024 * 32): Process1[ByteVector,ByteVector] = {
    suspend {
      val crc = new CRC32()
      var length = 0L
      val deflater = new Deflater(level, nowrap)
      val buf = Array.ofDim[Byte](bufferSize)

      @tailrec
      def collect(flush: Int, acc: Vector[ByteVector] = Vector.empty): Vector[ByteVector] =
        deflater.deflate(buf, 0, buf.length, flush) match {
          case 0 => acc
          case n => collect(flush, acc :+ ByteVector.view(buf.take(n)))
        }

      def go(): Process1[ByteVector,ByteVector] =
        receive1 { bytes =>
          val arr = bytes.toArray
          crc.update(arr)
          length += bytes.length
          deflater.setInput(arr)
          val chunks = collect(Deflater.NO_FLUSH)
          emitAll(chunks) ++ go()
        }

      def flush(): Process0[ByteVector] = {
        deflater.finish()
        val vecs = collect(Deflater.FULL_FLUSH)
        deflater.end()
        emitAll(vecs) ++ emit(trailer)
      }

      def trailer =
        ByteVector.view(
          ByteBuffer
            .allocate(Integer.BYTES * 2)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(crc.getValue.toInt)
            .putInt((length % GZIP_LENGTH_MOD).toInt)
            .array())

      emit(header) ++ (go() onComplete flush())
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

  private val header: ByteVector = ByteVector(
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
