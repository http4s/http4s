/* checkAll and friends were copied from the scalaz-specs2 project.
 * Source file: src/main/scala/Spec.scala
 * Project address: https://github.com/typelevel/scalaz-specs2
 * Copyright (C) 2013 Lars Hupel
 * License: MIT. https://github.com/typelevel/scalaz-specs2/blob/master/LICENSE.txt
 * Commit df921e18cf8bf0fd0bb510133f1ca6e1caea512b
 * Copied on. 11/1/2015
 */

package org.http4s

import cats.effect.{IO, Timer}
import cats.implicits.{catsSyntaxEither => _}
// import java.util.concurrent.{ScheduledExecutorService, ScheduledThreadPoolExecutor, TimeUnit}
import scala.concurrent.ExecutionContext

trait PlatformHttp4sSpec {
  // val TestScheduler: ScheduledExecutorService = {
  //   val s = new ScheduledThreadPoolExecutor(2, threadFactory(i => "http4s-test-scheduler", true))
  //   s.setKeepAliveTime(10L, TimeUnit.SECONDS)
  //   s.allowCoreThreadTimeOut(true)
  //   s
  // }


  val TestExecutionContext: ExecutionContext =
    ExecutionContext.global

  val TestTimer: Timer[IO] =
    IO.timer(TestExecutionContext)
}
