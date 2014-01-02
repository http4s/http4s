package org.http4s

import org.http4s.util.{Writer, Renderable}

sealed abstract class LanguageRange extends Renderable {
  def primaryTag: String
  def subTags: Seq[String]
  //val value = (primaryTag +: subTags).mkString("-")
  override def toString = "LanguageRange(" + value + ')'

  def render[W <: Writer](writer: W) = {
    writer.append(primaryTag)
    subTags.foreach(s => writer.append('-').append(s))
    writer
  }
}

object LanguageTag {

  case object `*` extends LanguageRange {
    def primaryTag = "*"
    def subTags = Seq.empty[String]
  }

}

case class LanguageTag(primaryTag: String, subTags: String*) extends LanguageRange

