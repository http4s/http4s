package org.http4s.util

import java.util.zip.{CRC32, Deflater}
import javax.xml.bind.DatatypeConverter

import cats.Functor
import fs2.compress.{deflate, inflate}
import fs2.{Chunk, Pipe, Pull, Pure, Segment, Stream}
import fs2.Stream.{chunk => fs2chunk} // because of org.http4s.util.chunk
import org.http4s.headers.{`Content-Encoding`, `Content-Length`, `Transfer-Encoding`}
import org.http4s.{ContentCoding, Response, TransferCoding}
import org.log4s.getLogger

object Compression {

  private[this] val logger = getLogger

  def zipResponse[F[_]: Functor](bufferSize: Int, level: Int, resp: Response[F]): Response[F] = {
    logger.trace("GZip middleware encoding content")
    // Need to add the Gzip header and trailer
    val trailerGen = new TrailerGen()
    val b = fs2chunk(header) ++
      resp.body
        .through(trailer(trailerGen))
        .through(
          deflate(
            level = level,
            nowrap = true,
            bufferSize = bufferSize
          )) ++
      fs2chunk(trailerFinish(trailerGen))
    resp
      .removeHeader(`Content-Length`)
      .putHeaders(`Content-Encoding`(ContentCoding.gzip))
      .copy(body = b)
  }

  def unzipResponse[F[_]: Functor](bufferSize: Int, resp: Response[F]): Response[F] = {
    logger.trace("GZip middleware decoding content")
    val b = fs2chunk(header) ++
      resp.body
        .through(
          inflate(
            nowrap = true,
            bufferSize = bufferSize
          ))
    resp
      .removeHeader(`Content-Encoding`)
      .putHeaders(`Transfer-Encoding`(TransferCoding.gzip))
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

  private def trailer[F[_]](gen: TrailerGen): Pipe[Pure, Byte, Byte] =
    _.pull.uncons.flatMap(trailerStep(gen)).stream

  private def trailerStep(
      gen: TrailerGen): (Option[(Segment[Byte, Unit], Stream[Pure, Byte])]) => Pull[
    Pure,
    Byte,
    Option[Stream[Pure, Byte]]] = {
    case None => Pull.pure(None)
    case Some((segment, stream)) =>
      val chunkArray = segment.toChunk.toArray
      gen.crc.update(chunkArray)
      gen.inputLength = gen.inputLength + chunkArray.length
      Pull.output(segment) >> stream.pull.uncons.flatMap(trailerStep(gen))
  }

  private def trailerFinish(gen: TrailerGen): Chunk[Byte] =
    Chunk.bytes(
      DatatypeConverter.parseHexBinary("%08x".format(gen.crc.getValue)).reverse ++
        DatatypeConverter.parseHexBinary("%08x".format(gen.inputLength % GZIP_LENGTH_MOD)).reverse)

}
