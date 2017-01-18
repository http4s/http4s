/* checkAll and friends were copied from the scalaz-specs2 project.
 * Source file: src/main/scala/Spec.scala
 * Project address: https://github.com/typelevel/scalaz-specs2
 * Copyright (C) 2013 Lars Hupel
 * License: MIT. https://github.com/typelevel/scalaz-specs2/blob/master/LICENSE.txt
 * Commit df921e18cf8bf0fd0bb510133f1ca6e1caea512b
 * Copied on. 11/1/2015
 */

package org.http4s

import java.util.concurrent.ExecutorService

import fs2._
import fs2.text._
import org.http4s.util.threads._
import org.http4s.testing._
import org.specs2.ScalaCheck
import org.specs2.execute.AsResult
import org.specs2.scalacheck.Parameters
import org.specs2.matcher._
import org.specs2.mutable.Specification
import org.specs2.specification.dsl.FragmentsDsl
import org.specs2.specification.create.{DefaultFragmentFactory=>ff}
import org.specs2.specification.core.Fragments
import org.scalacheck._
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.util.{FreqMap, Pretty}
import org.typelevel.discipline.Laws
import org.typelevel.discipline.specs2.mutable.Discipline
import org.typelevel.discipline.Laws

/**
 * Common stack for http4s' own specs.
 *
 * Not published in testing's main, because it doesn't depend on specs2.
 */
trait Http4sSpec extends Specification
  with ScalaCheck
  with AnyMatchers
  with OptionMatchers
  with Http4s
  with ArbitraryInstances
  with FragmentsDsl
  with Discipline
  with Batteries0
  with Http4sMatchers
{
  implicit val params = Parameters(maxSize = 20)

  implicit class ParseResultSyntax[A](self: ParseResult[A]) {
    def yolo: A = self.valueOr(e => sys.error(e.toString))
  }

  implicit class HttpServiceSyntax(service: HttpService) {
    def orNotFound(req: Request): Task[Response] =
      service.run(req).map(_.orNotFound)
  }

  /** This isn't really ours to provide publicly in implicit scope */
  implicit lazy val arbitraryByteChunk: Arbitrary[Chunk[Byte]] =
    Arbitrary {
      Gen.containerOf[Array, Byte](arbitrary[Byte])
        .map { b => Chunk.bytes(b) }
    }

  def writeToString[A](a: A)(implicit W: EntityEncoder[A]): String =
    Stream.eval(W.toEntity(a))
      .flatMap { case Entity(body, _ ) => body }
      .through(utf8Decode)
      .foldMonoid
      .runLast
      .map(_.getOrElse(""))
      .unsafeRun

  def writeToByteVector[A](a: A)(implicit W: EntityEncoder[A]): Chunk[Byte] =
    Stream.eval(W.toEntity(a))
      .flatMap { case Entity(body, _ ) => body }
      .bufferAll
      .chunks
      .runLast
      .map(_.getOrElse(Chunk.empty))
      .unsafeRun

  def checkAll(name: String, props: Properties)(implicit p: Parameters, f: FreqMap[Set[Any]] => Pretty): Fragments = {
    addFragment(ff.text(s"$name  ${props.name} must satisfy"))
    addFragments(Fragments.foreach(props.properties) { case (name, prop) => 
      Fragments(name in check(prop, p, f)) 
    })
  }

  def checkAll(props: Properties)(implicit p: Parameters, f: FreqMap[Set[Any]] => Pretty): Fragments = {
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
}

object Http4sSpec {
  // This is probably in poor taste, but I want to get things working.
  implicit val TestPool: ExecutorService = {
    val tf = threadFactory(l => s"http4s-spec-$l", daemon = true)
    newDefaultFixedThreadPool(8, tf)
  }

  implicit val TestPoolStrategy: Strategy =
    Strategy.fromExecutor(TestPool)

  implicit val TestScheduler: Scheduler =
    Scheduler.fromFixedDaemonPool(4)
}
