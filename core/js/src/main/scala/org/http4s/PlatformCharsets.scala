package org.http4s

import java.nio.charset.{Charset => NioCharset}
import java.nio.charset.StandardCharsets

trait PlatformCharsets {
  val availableCharsets: Seq[NioCharset] =
    Seq(
      StandardCharsets.US_ASCII,
      StandardCharsets.ISO_8859_1,
      StandardCharsets.UTF_8,
      StandardCharsets.UTF_16,
      StandardCharsets.UTF_16BE,
      StandardCharsets.UTF_16LE,
    )
}
