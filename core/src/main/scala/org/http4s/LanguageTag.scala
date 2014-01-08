package org.http4s

import org.http4s.util.{Writer, Renderable}
import scala.annotation.tailrec

object LanguageTag {

  val `*` = LanguageTag("*", Q.Unity)

  def apply(primaryTag: String, subTags: String*): LanguageTag = LanguageTag(primaryTag, Q.Unity, subTags)

//  def apply(primaryTag: String): LanguageTag = LanguageTag(primaryTag, Q.Unity)
//  def apply(primaryTag: String, subTags: String*): LanguageTag = LanguageTag(primaryTag, Q.Unity, subTags)
}

case class LanguageTag(primaryTag: String, q: Q = Q.Unity, subTags: Seq[String] = Nil) extends Renderable {
  def withQuality(q: Q): LanguageTag = LanguageTag(primaryTag, q, subTags)

  def render[W <: Writer](writer: W) = {
    writer.append(primaryTag)
    subTags.foreach(s => writer.append('-').append(s))
    q.render(writer)
    writer
  }

  @tailrec
  private def checkLists(tags1: Seq[String], tags2: Seq[String]): Boolean = {
    if (tags1.isEmpty) true
    else if (tags2.isEmpty || tags1.head != tags2.head) false
    else checkLists(tags1.tail, tags2.tail)
  }

  def satisfies(encoding: LanguageTag) = encoding.satisfiedBy(this)
  def satisfiedBy(encoding: LanguageTag) = {
    (this.primaryTag == "*" || this.primaryTag == encoding.primaryTag) &&
      !(q.unacceptable || encoding.q.unacceptable) &&
      q.intValue <= encoding.q.intValue &&
      checkLists(subTags, encoding.subTags)
  }
}

