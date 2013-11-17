package org.http4s

import java.nio.charset.Charset
import org.http4s.util.Registry

sealed abstract class CharacterSetRange {
  def value: String
  def matches(charset: CharacterSet): Boolean
  override def toString = "HttpCharsetRange(" + value + ')'
}

sealed abstract class CharacterSet extends CharacterSetRange {
  val charset: Charset = Charset.forName(value)
  def aliases: Seq[String]
  def matches(charset: CharacterSet) = this == charset
  override def equals(obj: Any) = obj match {
    case x: CharacterSet => (this eq x) || value == x.value
    case _ => false
  }
  override def hashCode() = value.##
  override def toString = "HttpCharset(" + value + ')'
}

// see http://www.iana.org/assignments/character-sets
object CharacterSet extends Registry[String, CharacterSet] {

  def register(charset: CharacterSet): CharacterSet = {
    register(charset.value.toLowerCase, charset)
    charset.aliases.foreach(alias => register(alias.toLowerCase, charset))
    charset
  }

  object `*` extends CharacterSetRange {
    def value = "*"
    def matches(charset: CharacterSet) = true
  }

  private class PredefCharacterSet(val value: String, val aliases: String*) extends CharacterSet

  val `US-ASCII`     = register(new PredefCharacterSet("US-ASCII", "iso-ir-6", "ANSI_X3.4-1986", "ISO_646.irv:1991", "ASCII", "ISO646-US", "us", "IBM367", "cp367", "csASCII"))
  val `ISO-8859-1`   = register(new PredefCharacterSet("ISO-8859-1", "iso-ir-100", "ISO_8859-1", "latin1", "l1", "IBM819", "CP819", "csISOLatin1"))
  val `ISO-8859-2`   = register(new PredefCharacterSet("ISO-8859-2", "iso-ir-101", "ISO_8859-2", "latin2", "l2", "csISOLatin2"))
  val `ISO-8859-3`   = register(new PredefCharacterSet("ISO-8859-3", "iso-ir-109", "ISO_8859-3", "latin3", "l3", "csISOLatin3"))
  val `ISO-8859-4`   = register(new PredefCharacterSet("ISO-8859-4", "iso-ir-110", "ISO_8859-4", "latin4", "l4", "csISOLatin4"))
  val `ISO-8859-5`   = register(new PredefCharacterSet("ISO-8859-5", "iso-ir-144", "ISO_8859-5", "cyrillic", "csISOLatinCyrillic"))
  val `ISO-8859-6`   = register(new PredefCharacterSet("ISO-8859-6", "iso-ir-127", "ISO_8859-6", "ECMA-114", "ASMO-708", "arabic", "csISOLatinArabic"))
  val `ISO-8859-7`   = register(new PredefCharacterSet("ISO-8859-7", "iso-ir-126", "ISO_8859-7", "ELOT_928", "ECMA-118", "greek", "greek8", "csISOLatinGreek"))
  val `ISO-8859-8`   = register(new PredefCharacterSet("ISO-8859-8", "iso-ir-138", "ISO_8859-8", "hebrew", "csISOLatinHebrew"))
  val `ISO-8859-9`   = register(new PredefCharacterSet("ISO-8859-9", "iso-ir-148", "ISO_8859-9", "latin5", "l5", "csISOLatin5"))
  val `ISO-8859-10`  = register(new PredefCharacterSet("ISO-8859-1", "iso-ir-157", "l6", "ISO_8859-10", "csISOLatin6", "latin6"))
  val `UTF-8`        = register(new PredefCharacterSet("UTF-8", "UTF8"))
  val `UTF-16`       = register(new PredefCharacterSet("UTF-16", "UTF16"))
  val `UTF-16BE`     = register(new PredefCharacterSet("UTF-16BE"))
  val `UTF-16LE`     = register(new PredefCharacterSet("UTF-16LE"))
  val `UTF-32`       = register(new PredefCharacterSet("UTF-32", "UTF32"))
  val `UTF-32BE`     = register(new PredefCharacterSet("UTF-32BE"))
  val `UTF-32LE`     = register(new PredefCharacterSet("UTF-32LE"))
  val `windows-1250` = register(new PredefCharacterSet("windows-1250", "cp1250", "cp5346"))
  val `windows-1251` = register(new PredefCharacterSet("windows-1251", "cp1251", "cp5347"))
  val `windows-1252` = register(new PredefCharacterSet("windows-1252", "cp1252", "cp5348"))
  val `windows-1253` = register(new PredefCharacterSet("windows-1253", "cp1253", "cp5349"))
  val `windows-1254` = register(new PredefCharacterSet("windows-1254", "cp1254", "cp5350"))
  val `windows-1257` = register(new PredefCharacterSet("windows-1257", "cp1257", "cp5353"))

  class CustomCharacterSet private (val value: String, val aliases: Seq[String]) extends CharacterSet
  object CustomCharacterSet {
    def apply(value: String, aliases: Seq[String] = Nil): Option[CustomCharacterSet] = {
      try {
        Some(new CustomCharacterSet(value.toLowerCase, aliases))
      } catch {
        case e: java.nio.charset.UnsupportedCharsetException => None
      }
    }
  }
}
