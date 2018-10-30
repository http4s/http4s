package org.http4s.internal

import java.nio.charset.{Charset, StandardCharsets}
import fs2.Chunk
import org.http4s.util.Writer
import scala.collection.mutable.Buffer

/** [[Writer]] that will result in a `Chunk`
  * @param toChunk initial `Chunk`
  */
private[http4s] class ChunkWriter(
    charset: Charset = StandardCharsets.UTF_8
) extends Writer {
  private[this] val chunks = Buffer[Chunk[Byte]]()

  def toChunk: Chunk[Byte] = Chunk.concatBytes(chunks)

  override def append(s: String): this.type = {
    chunks += Chunk.bytes(s.getBytes(charset))
    this
  }
}
