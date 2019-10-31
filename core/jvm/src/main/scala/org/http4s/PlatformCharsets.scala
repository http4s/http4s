package org.http4s

import java.nio.charset.{Charset => NioCharset}
import scala.collection.JavaConverters._

trait PlatformCharsets {
  val availableCharsets: Seq[NioCharset] =
    NioCharset.availableCharsets.values.asScala.toSeq
}
