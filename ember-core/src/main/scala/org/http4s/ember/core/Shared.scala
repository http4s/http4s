/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.ember.core

import fs2._
import scodec.bits.ByteVector

private[core] object Shared {
  val `\n` : ByteVector = ByteVector('\n')
  val `\r` : ByteVector = ByteVector('\r')
  val `\r\n` : ByteVector = ByteVector('\r', '\n')
  val `\r\n\r\n` = (`\r\n` ++ `\r\n`).compact

  def chunk2ByteVector(chunk: Chunk[Byte]): ByteVector =
    chunk match {
      case bv: Chunk.ByteVectorChunk => bv.toByteVector
      case other =>
        val bs = other.toBytes
        ByteVector(bs.values, bs.offset, bs.size)
    }
}
