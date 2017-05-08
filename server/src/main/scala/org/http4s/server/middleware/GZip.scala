package org.http4s
package server
package middleware

import java.util.zip.{CRC32, Deflater}
import javax.xml.bind.DatatypeConverter

import fs2._
import fs2.Stream._
import fs2.compress._
import fs2.interop.cats._
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
                val b = chunk(header) ++ resp.body.through(deflateWithTrailer(level, bufferSize))
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

  private final case class TrailerGen(crc: CRC32 = new CRC32(), var inputLength: Int = 0)

  private def deflateWithTrailer[F[_]](level: Int, bufferSize: Int): Pipe[F, Byte, Byte] = {
    val trailer = TrailerGen()
    pipe.covary[F, Byte, Byte](_.pull(_.await.flatMap(deflateStep(trailer, level, bufferSize)).or(deflateFinish(trailer))))
  }

  private def deflateStep(trailer: TrailerGen, level: Int, bufferSize: Int): ((Chunk[Byte], Handle[Pure, Byte])) => Pull[Pure, Byte, Handle[Pure, Byte]] = {
    case (c, h) =>
      val chunkArr = c.toArray
      trailer.crc.update(chunkArr)
      trailer.inputLength = trailer.inputLength + chunkArr.length
      Pull.outputs(deflate(
        level = level,
        nowrap = true,
        bufferSize = bufferSize
      )(chunk(c))) >> deflateHandle(trailer, level, bufferSize)(h)
  }

  private def deflateHandle(trailer: TrailerGen, level: Int, bufferSize: Int)(h: Handle[Pure, Byte]): Pull[Pure, Byte, Handle[Pure, Byte]] =
    h.await.flatMap(deflateStep(trailer, level, bufferSize))

  private def deflateFinish(trailer: TrailerGen): Pull[Pure, Byte, Nothing] = {
    // Temporary workaround to fix incorrect deflation of an empty stream in fs2
    // See https://github.com/functional-streams-for-scala/fs2/pull/865
    val extraBytes = if (trailer.inputLength == 0) Array(3.toByte, 0.toByte) else Array[Byte]()
    Pull.output(Chunk.bytes(
      extraBytes ++
        DatatypeConverter.parseHexBinary("%08x".format(trailer.crc.getValue())).reverse ++
        DatatypeConverter.parseHexBinary("%08x".format(trailer.inputLength % GZIP_LENGTH_MOD)).reverse)) >> Pull.done
  }
}
