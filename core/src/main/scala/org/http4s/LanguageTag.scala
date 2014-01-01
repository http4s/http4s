package org.http4s

import org.http4s.util.Renderable

sealed abstract class LanguageRange extends Renderable {
  def primaryTag: String
  def subTags: Seq[String]
  //val value = (primaryTag +: subTags).mkString("-")
  override def toString = "LanguageRange(" + value + ')'

  def render(builder: StringBuilder): StringBuilder = (primaryTag +: subTags).addString(builder, "-")
}

object LanguageTag {

  case object `*` extends LanguageRange {
    def primaryTag = "*"
    def subTags = Seq.empty[String]
  }

}

case class LanguageTag(primaryTag: String, subTags: String*) extends LanguageRange

