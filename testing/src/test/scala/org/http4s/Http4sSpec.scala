/* checkAll and friends were copied from the scalaz-specs2 project.
 * Source file: src/main/scala/Spec.scala
 * Project address: https://github.com/typelevel/scalaz-specs2
 * Copyright (C) 2013 Lars Hupel
 * License: MIT. https://github.com/typelevel/scalaz-specs2/blob/master/LICENSE.txt
 * Commit df921e18cf8bf0fd0bb510133f1ca6e1caea512b
 * Copied on. 11/1/2015
 */

package org.http4s

import cats.effect.{ContextShift, ExitCase, IO, Resource, Timer}
import cats.implicits.{catsSyntaxEither => _, _}
import fs2._
import fs2.text._
import java.util.concurrent.{ScheduledExecutorService, ScheduledThreadPoolExecutor, TimeUnit}
import org.http4s.testing._
import org.http4s.util.threads.{newBlockingPool, newDaemonPool, threadFactory}
import org.scalacheck._
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.util.{FreqMap, Pretty}
import org.specs2.ScalaCheck
import org.specs2.execute.{Result, Skipped}
import org.specs2.matcher._
import org.specs2.mutable.Specification
import org.specs2.scalacheck.Parameters
import org.specs2.specification.core.Fragments
import org.specs2.specification.create.{DefaultFragmentFactory => ff}
import org.specs2.specification.dsl.FragmentsDsl
import org.typelevel.discipline.specs2.mutable.Discipline
import scala.concurrent.ExecutionContext

/**
  * Common stack for http4s' own specs.
  *
  * Not published in testing's main, because it doesn't depend on specs2.
  */
trait Http4sSpec
    extends Specification
    with ScalaCheck
    with AnyMatchers
    with OptionMatchers
    with syntax.AllSyntax
    with ArbitraryInstances
    with FragmentsDsl
    with Discipline
    with IOMatchers
    with Http4sMatchers[IO] {
  implicit def testExecutionContext: ExecutionContext = Http4sSpec.TestExecutionContext
  val testBlockingExecutionContext: ExecutionContext = Http4sSpec.TestBlockingExecutionContext
  implicit val contextShift: ContextShift[IO] = Http4sSpec.TestContextShift
  implicit val timer: Timer[IO] = Http4sSpec.TestTimer
  def scheduler: ScheduledExecutorService = Http4sSpec.TestScheduler

  implicit val params = Parameters(maxSize = 20)

  implicit class ParseResultSyntax[A](self: ParseResult[A]) {
    def yolo: A = self.valueOr(e => sys.error(e.toString))
  }

  /** This isn't really ours to provide publicly in implicit scope */
  implicit lazy val arbitraryByteChunk: Arbitrary[Chunk[Byte]] =
    Arbitrary {
      Gen
        .containerOf[Array, Byte](arbitrary[Byte])
        .map { b =>
          Chunk.bytes(b)
        }
    }

  def writeToString[A](a: A)(implicit W: EntityEncoder[IO, A]): String =
    Stream
      .emit(W.toEntity(a))
      .covary[IO]
      .flatMap(_.body)
      .through(utf8Decode)
      .foldMonoid
      .compile
      .last
      .map(_.getOrElse(""))
      .unsafeRunSync

  def writeToChunk[A](a: A)(implicit W: EntityEncoder[IO, A]): Chunk[Byte] =
    Stream
      .emit(W.toEntity(a))
      .covary[IO]
      .flatMap(_.body)
      .bufferAll
      .chunks
      .compile
      .last
      .map(_.getOrElse(Chunk.empty))
      .unsafeRunSync

  def checkAll(name: String, props: Properties)(
      implicit p: Parameters,
      f: FreqMap[Set[Any]] => Pretty): Fragments = {
    addFragment(ff.text(s"$name  ${props.name} must satisfy"))
    addFragments(Fragments.foreach(props.properties) {
      case (name, prop) =>
        Fragments(name in check(prop, p, f))
    })
  }

  def checkAll(
      props: Properties)(implicit p: Parameters, f: FreqMap[Set[Any]] => Pretty): Fragments = {
    addFragment(ff.text(s"${props.name} must satisfy"))
    addFragments(Fragments.foreach(props.properties) {
      case (name, prop) =>
        Fragments(name in check(prop, p, f))
    })
  }

  implicit def enrichProperties(props: Properties) = new {
    def withProp(propName: String, prop: Prop) = new Properties(props.name) {
      for { (name, p) <- props.properties } property(name) = p
      property(propName) = prop
    }
  }

  def beStatus(status: Status): Matcher[Response[IO]] = { resp: Response[IO] =>
    (resp.status == status) -> s" doesn't have status $status"
  }

  def withResource[A](r: Resource[IO, A])(fs: A => Fragments): Fragments =
    r match {
      case Resource.Allocate(alloc) =>
        alloc
          .map {
            case (a, release) =>
              fs(a).append(step(release(ExitCase.Completed).unsafeRunSync()))
          }
          .unsafeRunSync()
      case Resource.Bind(r, f) =>
        withResource(r)(a => withResource(f(a))(fs))
      case Resource.Suspend(r) =>
        withResource(r.unsafeRunSync() /* ouch */ )(fs)
    }

  /** These tests are flaky on Travis.  Use sparingly and with great shame. */
  def skipOnCi(f: => Result): Result =
    if (sys.env.get("CI").isDefined) Skipped("Flakier than it's worth on CI")
    else f
}

object Http4sSpec {
  val TestExecutionContext: ExecutionContext =
    ExecutionContext.fromExecutor(newDaemonPool("http4s-spec", timeout = true))

  val TestBlockingExecutionContext: ExecutionContext =
    ExecutionContext.fromExecutor(newBlockingPool("http4s-spec-blocking"))

  val TestContextShift: ContextShift[IO] =
    IO.contextShift(TestExecutionContext)

  val TestScheduler: ScheduledExecutorService = {
    val s = new ScheduledThreadPoolExecutor(2, threadFactory(i => "http4s-test-scheduler", true))
    s.setKeepAliveTime(10L, TimeUnit.SECONDS)
    s.allowCoreThreadTimeOut(true)
    s
  }

  val TestTimer: Timer[IO] =
    IO.timer(TestExecutionContext, TestScheduler)
}
