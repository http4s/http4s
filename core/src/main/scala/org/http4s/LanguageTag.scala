/*
 * Derived from https://github.com/spray/spray/blob/v1.1-M7/spray-http/src/main/scala/spray/http/LanguageRange.scala
 *
 * Copyright (C) 2011-2012 spray.io
 * Based on code copyright (C) 2010-2011 by the BlueEyes Web Framework Team (http://github.com/jdegoes/blueeyes)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.http4s

import org.http4s.util.{Renderable, Writer}
import scala.annotation.tailrec

object LanguageTag {

  val `*` = LanguageTag("*", QValue.One)

  def apply(primaryTag: String, subTags: String*): LanguageTag =
    LanguageTag(primaryTag, QValue.One, subTags.toList)

//  def apply(primaryTag: String): LanguageTag = LanguageTag(primaryTag, Q.Unity)
//  def apply(primaryTag: String, subTags: String*): LanguageTag = LanguageTag(primaryTag, Q.Unity, subTags)
}

final case class LanguageTag(
    primaryTag: String,
    q: QValue = QValue.One,
    subTags: List[String] = Nil)
    extends Renderable {
  @deprecated("Use languageTag.withQValue", "0.16.1")
  def withQuality(q: QValue): LanguageTag = LanguageTag(primaryTag, q, subTags)

  def withQValue(q: QValue): LanguageTag = copy(q = q)

  def render(writer: Writer): writer.type = {
    writer.append(primaryTag)
    subTags.foreach(s => writer.append('-').append(s))
    q.render(writer)
    writer
  }

  @tailrec
  private def checkLists(tags1: Seq[String], tags2: List[String]): Boolean =
    if (tags1.isEmpty) true
    else if (tags2.isEmpty || tags1.head != tags2.head) false
    else checkLists(tags1.tail, tags2.tail)

  @deprecated("Use `Accept-Language`.satisfiedBy(encoding)", "0.16.1")
  def satisfies(encoding: LanguageTag): Boolean = encoding.satisfiedBy(this)

  @deprecated("Use `Accept-Language`.satisfiedBy(encoding)", "0.16.1")
  def satisfiedBy(encoding: LanguageTag): Boolean =
    (this.primaryTag == "*" || this.primaryTag == encoding.primaryTag) &&
      q.isAcceptable && encoding.q.isAcceptable &&
      q <= encoding.q &&
      checkLists(subTags, encoding.subTags)

  def matches(languageTag: LanguageTag): Boolean =
    this.primaryTag == "*" || (this.primaryTag == languageTag.primaryTag &&
      checkLists(subTags, languageTag.subTags))
}
