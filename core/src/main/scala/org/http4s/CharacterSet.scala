package org.http4s

import java.nio.charset.Charset
import scala.collection.JavaConverters._
import org.http4s.util.CaseInsensitiveString
import scala.util.hashing.MurmurHash3

sealed trait CharacterSet extends HttpValue[String] with QualityFactor {

  def name: CaseInsensitiveString
  def charset: Charset
  def q: Q
  def satisfiedBy(characterSet: CharacterSet): Boolean
  def withQuality(q: Q): CharacterSet

  final def satisfies(characterSet: CharacterSet): Boolean = characterSet.satisfiedBy(this)

  def value: String = if (q.intValue == Q.MAX_VALUE) name.toString else name.toString + q.headerString

  override def equals(that: Any): Boolean = that match {
    case that: CharacterSet => that.name == this.name && that.q == this.q
    case _ => false
  }

  final override def hashCode(): Int = MurmurHash3.mixLast(name.hashCode, q.hashCode)
}

private class CharacterSetImpl(val name: CaseInsensitiveString, val q: Q = Q.Unity)
                                    extends CharacterSet {

  val charset: Charset = Charset.forName(name.toString)

  def satisfiedBy(characterSet: CharacterSet): Boolean = {
    this.q.intValue != 0  &&  // a q=0.0 means this charset is invalid
    this.name == characterSet.name
  }

  def withQuality(q: Q): CharacterSet = new CharacterSetImpl(name, q)
}

object CharacterSet extends Resolvable[CaseInsensitiveString, CharacterSet] {

  protected def stringToRegistryKey(s: String): CaseInsensitiveString = s.ci

  protected def fromKey(k: CaseInsensitiveString): CharacterSet = {
    if (k == `*`.value) `*`
    else new CharacterSetImpl(k)
  }

  private def register(name: String): CharacterSet = {
    val characterSet = new CharacterSetImpl(name.ci)
    register(characterSet.name, characterSet)
    for (alias <- characterSet.charset.aliases.asScala) register(alias.ci, characterSet)
    characterSet
  }

  private class AnyCharset(val q: Q) extends CharacterSet {
    def name: CaseInsensitiveString = "*".ci
    def satisfiedBy(characterSet: CharacterSet): Boolean = q.intValue != 0
    def charset: Charset = Charset.defaultCharset() // Give the system default
    override def withQuality(q: Q): CharacterSet = new AnyCharset(q)
  }

  val `*`: CharacterSet = new AnyCharset(Q.Unity)

  // These six are guaranteed to be on the Java platform. Others are your gamble.
  val `US-ASCII`     = register("US-ASCII")
  val `ISO-8859-1`   = register("ISO-8859-1")
  val `UTF-8`        = register("UTF-8")
  val `UTF-16`       = register("UTF-16")
  val `UTF-16BE`     = register("UTF-16BE")
  val `UTF-16LE`     = register("UTF-16LE")


  // Charset are sorted by the quality value, from greatest to least
  implicit def characterSetrOrdering = new Ordering[CharacterSet] {
    def compare(x: CharacterSet, y: CharacterSet): Int = {
      implicitly[Ordering[Q]].compare(y.q, x.q)
    }
  }
}
