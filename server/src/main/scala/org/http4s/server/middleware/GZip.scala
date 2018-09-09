package org.http4s
package server
package middleware

import cats._
import cats.data.Kleisli
import fs2._
import fs2.Stream._
import fs2.compress._
import java.nio.{ByteBuffer, ByteOrder}
import java.util.zip.{CRC32, Deflater}
import org.http4s.headers._
import org.log4s.getLogger

object GZip {
  private[this] val logger = getLogger

  // TODO: It could be possible to look for F.pure type bodies, and change the Content-Length header after
  // TODO      zipping and buffering all the input. Just a thought.
  def apply[F[_]: Functor, G[_]: Functor](
      @deprecatedName('service) http: Http[F, G],
      bufferSize: Int = 32 * 1024,
      level: Int = Deflater.DEFAULT_COMPRESSION,
      isZippable: Response[G] => Boolean = defaultIsZippable[G](_: Response[G])): Http[F, G] =
    Kleisli { req: Request[G] =>
      req.headers.get(`Accept-Encoding`) match {
        case Some(acceptEncoding) if satisfiedByGzip(acceptEncoding) =>
          http.map(zipOrPass(_, bufferSize, level, isZippable)).apply(req)
        case _ => http(req)
      }
    }

  def defaultIsZippable[F[_]](resp: Response[F]): Boolean = {
    val contentType = resp.headers.get(`Content-Type`)
    resp.headers.get(`Content-Encoding`).isEmpty &&
    (contentType.isEmpty || contentType.get.mediaType.compressible ||
    (contentType.get.mediaType eq MediaType.application.`octet-stream`))
  }

  private def satisfiedByGzip(acceptEncoding: `Accept-Encoding`) =
    acceptEncoding.satisfiedBy(ContentCoding.gzip) || acceptEncoding.satisfiedBy(
      ContentCoding.`x-gzip`)

  private def zipOrPass[F[_]: Functor](
      response: Response[F],
      bufferSize: Int,
      level: Int,
      isZippable: Response[F] => Boolean): Response[F] =
    response match {
      case resp if isZippable(resp) => zipResponse(bufferSize, level, resp)
      case resp => resp // Don't touch it, Content-Encoding already set
    }

  private def zipResponse[F[_]: Functor](
      bufferSize: Int,
      level: Int,
      resp: Response[F]): Response[F] = {
    logger.trace("GZip middleware encoding content")
    // Need to add the Gzip header and trailer
    val trailerGen = new TrailerGen()
    val b = chunk(header) ++
      resp.body
        .through(trailer(trailerGen, bufferSize))
        .through(
          deflate(
            level = level,
            nowrap = true,
            bufferSize = bufferSize
          )) ++
      chunk(trailerFinish(trailerGen))
    resp
      .removeHeader(`Content-Length`)
      .putHeaders(`Content-Encoding`(ContentCoding.gzip))
      .copy(body = b)
  }

  private val GZIP_MAGIC_NUMBER = 0x8b1f
  private val GZIP_LENGTH_MOD = Math.pow(2, 32).toLong

  private val header: Chunk[Byte] = Chunk.bytes(
    Array(
      GZIP_MAGIC_NUMBER.toByte, // Magic number (int16)
      (GZIP_MAGIC_NUMBER >> 8).toByte, // Magic number  c
      Deflater.DEFLATED.toByte, // Compression method
      0.toByte, // Flags
      0.toByte, // Modification time (int32)
      0.toByte, // Modification time  c
      0.toByte, // Modification time  c
      0.toByte, // Modification time  c
      0.toByte, // Extra flags
      0.toByte
    ) // Operating system
  )

  private final class TrailerGen(val crc: CRC32 = new CRC32(), var inputLength: Int = 0)

  private def trailer[F[_]](gen: TrailerGen, maxReadLimit: Int): Pipe[Pure, Byte, Byte] =
    _.pull.unconsLimit(maxReadLimit).flatMap(trailerStep(gen, maxReadLimit)).stream

  private def trailerStep(gen: TrailerGen, maxReadLimit: Int): (
      Option[(Chunk[Byte], Stream[Pure, Byte])]) => Pull[Pure, Byte, Option[Stream[Pure, Byte]]] = {
    case None => Pull.pure(None)
    case Some((chunk, stream)) =>
      gen.crc.update(chunk.toArray)
      gen.inputLength = gen.inputLength + chunk.size
      Pull.output(chunk) >> stream.pull
        .unconsLimit(maxReadLimit)
        .flatMap(trailerStep(gen, maxReadLimit))
  }

  private def trailerFinish(gen: TrailerGen): Chunk[Byte] =
    Chunk.bytes(
      ByteBuffer
        .allocate(Integer.BYTES * 2)
        .order(ByteOrder.LITTLE_ENDIAN)
        .putInt(gen.crc.getValue.toInt)
        .putInt((gen.inputLength % GZIP_LENGTH_MOD).toInt)
        .array())
}
