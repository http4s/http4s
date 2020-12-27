/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Based on https://github.com/typelevel/scalaz-specs2/src/main/scala/Spec.scala
 * Copyright (C) 2013 Lars Hupel
 * See licenses/LICENSE_scalaz-specs2
 */

package org.http4s

import cats.effect.{IO, Resource}
import cats.syntax.all._
import fs2._
import fs2.text._
import java.util.concurrent.{ScheduledExecutorService, ScheduledThreadPoolExecutor, TimeUnit}
import org.http4s.internal.threads.{newBlockingPool, newDaemonPool, threadFactory}
import org.http4s.laws.discipline.ArbitraryInstances
import org.scalacheck._
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
import scala.concurrent.duration._
import cats.effect.unsafe.IORuntime
import cats.effect.unsafe.Scheduler

/** Common stack for http4s' own specs.
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
    with Discipline {

  implicit val testIORuntime: IORuntime = Http4sSpec.TestIORuntime

  protected val timeout: FiniteDuration = 10.seconds

  implicit val params = Parameters(maxSize = 20)

  implicit class ParseResultSyntax[A](self: ParseResult[A]) {
    def yolo: A = self.valueOr(e => sys.error(e.toString))
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
      .unsafeRunSync()

  def checkAll(name: String, props: Properties)(implicit
      p: Parameters,
      f: FreqMap[Set[Any]] => Pretty): Fragments = {
    addFragment(ff.text(s"$name  ${props.name} must satisfy"))
    addBreak
    addFragments(Fragments.foreach(props.properties.toList) { case (name, prop) =>
      Fragments(name in check(prop, p, f))
    })
  }

  def checkAll(
      props: Properties)(implicit p: Parameters, f: FreqMap[Set[Any]] => Pretty): Fragments = {
    addFragment(ff.text(s"${props.name} must satisfy"))
    addFragments(Fragments.foreach(props.properties.toList) { case (name, prop) =>
      Fragments(name in check(prop, p, f))
    })
  }

  implicit def enrichProperties(props: Properties) =
    new {
      def withProp(propName: String, prop: Prop) =
        new Properties(props.name) {
          for { (name, p) <- props.properties } property(name) = p
          property(propName) = prop
        }
    }

  def beStatus(status: Status): Matcher[Response[IO]] = { (resp: Response[IO]) =>
    (resp.status == status) -> s" doesn't have status $status"
  }

  def withResource[A](r: Resource[IO, A])(fs: A => Fragments): Fragments =
    r.allocated
      .map { case (r, release) => fs(r).append(step(release.unsafeRunTimed(timeout))) }
      .unsafeRunTimed(timeout)
      .getOrElse(throw new Exception(s"no result after $timeout"))

  /** These tests are flaky on Travis.  Use sparingly and with great shame. */
  def skipOnCi(f: => Result): Result =
    if (sys.env.get("CI").isDefined) Skipped("Flakier than it's worth on CI")
    else f
}

object Http4sSpec {
  val TestExecutionContext: ExecutionContext =
    ExecutionContext.fromExecutor(newDaemonPool("http4s-spec", timeout = true))

  val TestScheduler: ScheduledExecutorService = {
    val s =
      new ScheduledThreadPoolExecutor(2, threadFactory(i => s"http4s-test-scheduler-$i", true))
    s.setKeepAliveTime(10L, TimeUnit.SECONDS)
    s.allowCoreThreadTimeOut(true)
    s
  }

  val TestIORuntime: IORuntime = {
    val blockingPool = newBlockingPool("http4s-spec-blocking")
    // val computePool = newDaemonPool("http4s-spec", timeout = true)
    val computePool = newBlockingPool("http4s-spec")
    val scheduledExecutor = TestScheduler
    IORuntime.apply(
      ExecutionContext.fromExecutor(computePool),
      ExecutionContext.fromExecutor(blockingPool),
      Scheduler.fromScheduledExecutor(scheduledExecutor),
      () => {
        blockingPool.shutdown()
        computePool.shutdown()
        scheduledExecutor.shutdown()
      }
    )
  }

}
