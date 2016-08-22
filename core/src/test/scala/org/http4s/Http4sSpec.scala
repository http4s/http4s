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
import org.specs2.execute.AsResult
import org.specs2.scalacheck.Parameters
import org.specs2.matcher._
import org.specs2.mutable.Specification
import org.specs2.specification.dsl.FragmentsDsl
import org.specs2.specification.create.{DefaultFragmentFactory=>ff}
import org.specs2.specification.core.Fragments
import org.scalacheck.util.{FreqMap, Pretty}
import org.typelevel.discipline.Laws
import scalaz.{ -\/, \/- }
import scalaz.concurrent.Task
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
  with Http4sMatchers
  with TaskMatchers
{
  implicit val params = Parameters(maxSize = 20)

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

  def beStatus(status: Status): Matcher[Response] = { resp: Response =>
    (resp.status == status) -> s" doesn't have status ${status}"
  }

  implicit class TaskMatchable[T](m: Matcher[T]) {
    def run: Matcher[Task[T]] =
      runMatcher(m)

    private def runMatcher(m: Matcher[T]): Matcher[Task[T]] =
      new Matcher[Task[T]] {
        def apply[S <: Task[T]](a: Expectable[S]) = {
          a.value.attemptRun match {
            case \/-(v) =>
              val r = AsResult(createExpectable(v).applyMatcher(m))
              result(r.isSuccess, r.message, r.message, a)
            case -\/(t) =>
              val r = createExpectable(throw t).applyMatcher(m).toResult
              result(r.isSuccess, r.message, r.message, a)
          }
        }
      }
  }

  def checkAll(name: String, ruleSet: Laws#RuleSet)(implicit p: Parameters) = {
    s"""${ruleSet.name} laws must hold for ${name}""" in {
      Fragments.foreach(ruleSet.all.properties) { case (id, prop) =>
        id ! check(prop, p, defaultFreqMapPretty) ^ br
      }
    }
  }
}


