package org.http4s

import java.nio.charset.Charset
import org.http4s.util.CaseInsensitiveString
import scala.collection.JavaConverters._

sealed abstract class CharacterSetRange extends HttpValue[CaseInsensitiveString] {
  def matches(characterSet: CharacterSet): Boolean
}

sealed case class CharacterSet private (value: CaseInsensitiveString) extends CharacterSetRange {
  val charset: Charset = Charset.forName(value.toString)
  def name = value.toString
  def matches(characterSet: CharacterSet) = this == characterSet
}

object CharacterSet extends Resolvable[CaseInsensitiveString, CharacterSet] {
  protected def stringToRegistryKey(s: String): CaseInsensitiveString = s.ci

  protected def fromKey(k: CaseInsensitiveString): CharacterSet = new CharacterSet(k)

  private def register(name: String): CharacterSet = {
    val characterSet = new CharacterSet(name.ci)
    register(characterSet.name.ci, characterSet)
    for (alias <- characterSet.charset.aliases.asScala) register(alias.ci, characterSet)
    characterSet
  }

  object `*` extends CharacterSetRange {
    def value = "*".ci
    def matches(charset: CharacterSet) = true
  }

  // These six are guaranteed to be on the Java platform. Others are your gamble.
  val `US-ASCII`     = register("US-ASCII")
  val `ISO-8859-1`   = register("ISO-8859-1")
  val `UTF-8`        = register("UTF-8")
  val `UTF-16`       = register("UTF-16")
  val `UTF-16BE`     = register("UTF-16BE")
  val `UTF-16LE`     = register("UTF-16LE")
}
