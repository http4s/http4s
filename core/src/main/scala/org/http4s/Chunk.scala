package org.http4s

case class Chunk(bytes: Array[Byte], extension: List[ChunkExtension] = Nil)

case class ChunkExtension(name: String, value: String)
