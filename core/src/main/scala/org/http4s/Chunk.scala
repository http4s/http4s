package org.http4s

case class Chunk private (
  bytes: Array[Byte],
  extensions: Seq[ChunkExtension],
  trailer: Headers
)

object Chunk {
  def chunk(bytes: Array[Byte], extensions: Seq[ChunkExtension] = Nil): Chunk = Chunk(bytes, extensions, Headers.Empty)
  def last(extensions: Seq[ChunkExtension] = Nil, headers: Headers): Chunk = Chunk(Array.empty, extensions, headers)
}

case class ChunkExtension(name: String, value: String)

