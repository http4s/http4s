package org.http4s
package server
package middleware

import fs2._
import fs2.Stream._
import fs2.compress._
import fs2.interop.cats._
import java.nio.{ByteBuffer, ByteOrder}
import java.util.zip.{CRC32, Deflater}
import javax.xml.bind.DatatypeConverter
import org.http4s.headers._
import org.log4s.getLogger
import scala.annotation.tailrec
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
                val trailerGen = new TrailerGen()
                val b = chunk(header) ++
                  resp.body
                    .through(trailer(trailerGen))
                    .through(deflate(
                      level = level,
                      nowrap = true,
                      bufferSize = bufferSize
                    )) ++
                  chunk(trailerFinish(trailerGen))
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

  private final class TrailerGen(val crc: CRC32 = new CRC32(), var inputLength: Int = 0)

  private def trailer[F[_]](gen: TrailerGen): Pipe[F, Byte, Byte] =
    pipe.covary[F, Byte, Byte](_.pull(_.await.flatMap(trailerStep(gen))))

  private def trailerStep(gen: TrailerGen): ((Chunk[Byte], Handle[Pure, Byte])) => Pull[Pure, Byte, Handle[Pure, Byte]] = {
    case (c, h) =>
      val chunkArr = c.toArray
      gen.crc.update(chunkArr)
      gen.inputLength = gen.inputLength + chunkArr.length
      Pull.output(c) >> trailerHandle(gen)(h)
  }

  private def trailerHandle(gen: TrailerGen)(h: Handle[Pure, Byte]): Pull[Pure, Byte, Handle[Pure, Byte]] =
    h.await.flatMap(trailerStep(gen))

  private def trailerFinish(gen: TrailerGen): Chunk[Byte] = {
    Chunk.bytes(
      ByteBuffer
        .allocate(Integer.BYTES * 2)
        .order(ByteOrder.LITTLE_ENDIAN)
        .putInt(gen.crc.getValue.toInt)
        .putInt((gen.inputLength % GZIP_LENGTH_MOD).toInt)
        .array())
  }
}
