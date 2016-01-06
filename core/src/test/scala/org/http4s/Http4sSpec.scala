/* checkAll and friends were copied from the scalaz-specs2 project.
 * Source file: src/main/scala/Spec.scala
 * Project address: https://github.com/typelevel/scalaz-specs2
 * Copyright (C) 2013 Lars Hupel
 * License: MIT. https://github.com/typelevel/scalaz-specs2/blob/master/LICENSE.txt
 * Commit df921e18cf8bf0fd0bb510133f1ca6e1caea512b
 * Copied on. 11/1/2015
 */

package org.http4s

import org.specs2.ScalaCheck
import org.specs2.scalacheck.Parameters
import org.specs2.matcher.{TaskMatchers, AnyMatchers, OptionMatchers, DisjunctionMatchers}
import org.specs2.mutable.Specification
import org.specs2.specification.dsl.FragmentsDsl
import org.specs2.specification.create.{DefaultFragmentFactory=>ff}
import org.specs2.specification.core.Fragments
import org.scalacheck.util.{FreqMap, Pretty}

import scalaz.std.AllInstances
import org.scalacheck._

/**
 * Common stack for http4s' own specs.
 */
trait Http4sSpec extends Specification
  with ScalaCheck
  with AnyMatchers
  with OptionMatchers
  with DisjunctionMatchers
  with Http4s
  with TestInstances
  with AllInstances
  with FragmentsDsl
  with TaskMatchers
{
  def checkAll(name: String, props: Properties)(implicit p: Parameters, f: FreqMap[Set[Any]] => Pretty) {
    addFragment(ff.text(s"$name  ${props.name} must satisfy"))
    addFragments(Fragments.foreach(props.properties) { case (name, prop) => 
      Fragments(name in check(prop, p, f)) 
    })
  }

  def checkAll(props: Properties)(implicit p: Parameters, f: FreqMap[Set[Any]] => Pretty) {
    addFragment(ff.text(s"${props.name} must satisfy"))
    addFragments(Fragments.foreach(props.properties) { case (name, prop) => 
      Fragments(name in check(prop, p, f)) 
    })
  }

  implicit def enrichProperties(props: Properties) = new {
    def withProp(propName: String, prop: Prop) = new Properties(props.name) {
      for {(name, p) <- props.properties} property(name) = p
        property(propName) = prop
    }
  }
}

