/*
 * Derived from https://raw.githubusercontent.com/arktekk/anti-xml/973068681c74e22bc8c68f4c116720ed543cc0e3/src/test/scala/com/codecommit/antixml/XMLGenerators.scala
 *
 * Copyright (c) 2011, Daniel Spiewak
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice, this
 *   list of conditions and the following disclaimer in the documentation and/or
 *   other materials provided with the distribution.
 * - Neither the name of "Anti-XML" nor the names of its contributors may
 *   be used to endorse or promote products derived from this software without
 *   specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.http4s
package scalaxml

import org.scalacheck._
import scala.io.Source
import scala.xml._

trait XMLGenerators {
  import Arbitrary.arbitrary
  import Gen._

  val MaxGroupDepth = 3

  implicit val arbNode: Arbitrary[Node] = Arbitrary(nodeGenerator(0))

  implicit val arbProcInstr: Arbitrary[ProcInstr] = Arbitrary(procInstrGenerator)
  implicit val arbElem: Arbitrary[Elem] = Arbitrary(elemGenerator(0))
  implicit val arbText: Arbitrary[Text] = Arbitrary(textGenerator)
  implicit val arbEntityRef: Arbitrary[EntityRef] = Arbitrary(entityRefGenerator)

  implicit val arbAttributes: Arbitrary[MetaData] = Arbitrary(genAttributes)

  def nodeGenerator(depth: Int = 0): Gen[Node] = frequency(
    3 -> procInstrGenerator,
    30 -> elemGenerator(depth),
    50 -> textGenerator,
    0 -> entityRefGenerator)

  lazy val procInstrGenerator: Gen[ProcInstr] = for {
    target <- genSaneString
    data <- genSaneString
  } yield ProcInstr(target, data)

  def elemGenerator(depth: Int = 0): Gen[Elem] = for {
    ns <- genSaneOptionString
    name <- genSaneString
    attrs <- genAttributes
    bindings <- genBindings // TODO incorporate
    children <- if (depth > MaxGroupDepth) const(Nil) else (listOf(nodeGenerator(depth + 1)) map { nodes => Group(concatConsecutiveText(nodes)) })
  } yield {
    Elem(ns.orNull, name, attrs, TopScope, false, children:_*)
  }

  // Consecutive Text children are lossy in serialization
  private def concatConsecutiveText(nodes: List[Node]): List[Node] =
    nodes match {
      case Nil => Nil
      case (h: Text) :: t =>
        val texts = nodes.takeWhile(_.isInstanceOf[Text])
        val text = Text(texts.foldLeft(""){_ + _.asInstanceOf[Text].data})
        text :: concatConsecutiveText(nodes.drop(texts.length))
      case h :: t =>
        h :: concatConsecutiveText(t)
    }

  lazy val textGenerator: Gen[Text] = genSaneString map Text.apply

  lazy val entityRefGenerator: Gen[EntityRef] = genSaneString map EntityRef

  lazy val genSaneString: Gen[String] =
    // TODO empty is okay in some places here
    // TODO we can be more clever than alpha
    nonEmptyListOf(alphaChar).map(_.mkString)

  private lazy val genSaneOptionString: Gen[Option[String]] =
    frequency(5 -> (genSaneString map { Some(_) }), 1 -> None)

  private lazy val genAttributes: Gen[MetaData] =
    genAttributeList.map(_.foldLeft[MetaData](Null)(MetaData.concatenate(_, _)))

  lazy val genAttributeList: Gen[List[MetaData]] = {
    val genTuple = for {
      prefix <- genSaneOptionString
      name <- genSaneString
      value <- genSaneString
    } yield (prefix match {
      case Some(p) => new PrefixedAttribute(p, name, value, Null)
      case None => new UnprefixedAttribute(name, value, Null)
    })

    listOf(genTuple)
  }

  private lazy val genBindings: Gen[NamespaceBinding] = {
    val genTuple = for {
      prefix <- genSaneString
      uri <- genSaneString
    } yield (prefix, uri)

    listOf(genTuple).map(_.foldLeft[NamespaceBinding](TopScope)((parent, child) => NamespaceBinding(child._1, child._2, parent)))
  }
}
