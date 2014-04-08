package org.http4s

import java.nio.charset.Charset
import scala.collection.JavaConverters._
import org.http4s.util._
import scala.util.hashing.MurmurHash3
import org.http4s.util.string._

sealed trait CharacterSet extends QualityFactor with Renderable {

  def name: CaseInsensitiveString
  def charset: Charset
  def q: Q
  def satisfiedBy(characterSet: CharacterSet): Boolean
  def withQuality(q: Q): CharacterSet

  final def satisfies(characterSet: CharacterSet): Boolean = characterSet.satisfiedBy(this)

  def render[W <: Writer](writer: W) = {
    writer.append(name.toString)
    q.render(writer)
    writer
  }

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
    this.name == characterSet.name  &&
    !(q.unacceptable || characterSet.q.unacceptable)
  }

  def withQuality(q: Q): CharacterSet = new CharacterSetImpl(name, q)
}

// TODO: not finished!
object CharacterSet extends Registry {

  type Key = CaseInsensitiveString
  type Value = CharacterSet

  implicit def fromKey(k: CaseInsensitiveString): CharacterSet = {
    if (k.toString == "*") `*`
    else new CharacterSetImpl(k)
  }

  implicit def fromValue(v: CharacterSet): CaseInsensitiveString = v.name


  override protected def registerKey(key: CaseInsensitiveString)(implicit ev: (CaseInsensitiveString) => CharacterSet): CharacterSet = {
    val characterSet = ev(key.ci)
    register(characterSet.name, characterSet)
    for (alias <- characterSet.charset.aliases.asScala) register(alias.ci, characterSet)
    characterSet
  }

  private class AnyCharset(val q: Q) extends CharacterSet {
    val name: CaseInsensitiveString = "*".ci
    def satisfiedBy(characterSet: CharacterSet): Boolean = !(q.unacceptable || characterSet.q.unacceptable)
    def charset: Charset = Charset.defaultCharset() // Give the system default
    override def withQuality(q: Q): CharacterSet = new AnyCharset(q)
  }

  val `*`: CharacterSet = new AnyCharset(Q.Unity)

  // These six are guaranteed to be on the Java platform. Others are your gamble.
  val `US-ASCII`     = registerKey("US-ASCII".ci)
  val `ISO-8859-1`   = registerKey("ISO-8859-1".ci)
  val `UTF-8`        = registerKey("UTF-8".ci)
  val `UTF-16`       = registerKey("UTF-16".ci)
  val `UTF-16BE`     = registerKey("UTF-16BE".ci)
  val `UTF-16LE`     = registerKey("UTF-16LE".ci)


  // Charset are sorted by the quality value, from greatest to least
  implicit def characterSetrOrdering = new Ordering[CharacterSet] {
    def compare(x: CharacterSet, y: CharacterSet): Int = {
      implicitly[Ordering[Q]].compare(y.q, x.q)
    }
  }
}
