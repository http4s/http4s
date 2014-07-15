package org.http4s.util

import java.util.zip.{CRC32, Deflater}
import java.nio.{ByteOrder, ByteBuffer}

/** A non-thread safe GZip helper
  * 
  * @param buffersize Initial size of the outputbuffer, but it will grow as needed
  * @param compressionLevel Deflater compression level
  */
final class Gzipper(buffersize: Int, compressionLevel: Int = Deflater.DEFAULT_COMPRESSION) {
  import Gzipper._

  private var outputbuffer = new Bytes(buffersize)
  private var pos = 0
  private val crc = new CRC32()
  private val deflater = new Deflater(compressionLevel, true)

  writeHeader()

  private def ensureSpace(size: Int) {
    if (outputbuffer.length - pos < size) {
      val sz = 2*(size + pos)
      val old = outputbuffer
      outputbuffer = new Bytes(sz)
      System.arraycopy(old, 0, outputbuffer, 0, pos)
    }
  }

  private def writeHeader() {
    ensureSpace(header.length)
    System.arraycopy(header, 0, outputbuffer, 0, header.length)
    pos = header.length
  }

  def finish() {
    if (!deflater.finished()) {
      deflater.finish()
      while (!deflater.finished()) {  // Get any remaining bytes
        ensureSpace(buffersize)
        this.pos += deflater.deflate(outputbuffer, pos, outputbuffer.length - pos)
      }

      // Make sure we have enough room for the trailer
      if (pos + TRAILER_LENGTH > outputbuffer.length) {
        val old = outputbuffer
        outputbuffer = new Bytes(pos + TRAILER_LENGTH)
        System.arraycopy(old, 0, outputbuffer, 0, pos)
      }

      val buff = ByteBuffer.wrap(outputbuffer, pos, TRAILER_LENGTH).order(ByteOrder.LITTLE_ENDIAN)
      buff.putInt(crc.getValue.toInt)
      buff.putInt(deflater.getTotalIn)
      pos += TRAILER_LENGTH

      deflater.end() // Really makes a difference on memory consumption
    }
  }

  def write(bytes: Bytes): Unit = write(bytes, 0, bytes.length)

  def write(bytes: Bytes, pos: Int, length: Int) {
    crc.update(bytes, pos, length)
    deflater.setInput(bytes, pos, length)
    while (!deflater.needsInput()) {
      ensureSpace(buffersize)
      this.pos += deflater.deflate(outputbuffer, this.pos, outputbuffer.length - this.pos)
    }
  }

  def size() = pos

  def getBytes(): Bytes = {
    val b = new Bytes(pos)
    if (pos > 0) System.arraycopy(outputbuffer, 0, b, 0, pos)
    pos = 0
    b
  }
}

object Gzipper {
  type Bytes = Array[Byte]

  private val GZIP_MAGIC_NUMBER = 0x8b1f
  private val TRAILER_LENGTH = 8

  private val header: Bytes = Array(
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