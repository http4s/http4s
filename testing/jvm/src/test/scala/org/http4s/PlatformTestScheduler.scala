/* checkAll and friends were copied from the scalaz-specs2 project.
 * Source file: src/main/scala/Spec.scala
 * Project address: https://github.com/typelevel/scalaz-specs2
 * Copyright (C) 2013 Lars Hupel
 * License: MIT. https://github.com/typelevel/scalaz-specs2/blob/master/LICENSE.txt
 * Commit df921e18cf8bf0fd0bb510133f1ca6e1caea512b
 * Copied on. 11/1/2015
 */

package org.http4s

import cats.effect.IO
import fs2._

object PlatformTestScheduler {
  val TestScheduler: Scheduler = {
    val (sched, _) = Scheduler
      .allocate[IO](corePoolSize = 4, threadPrefix = "http4s-spec-scheduler")
      .unsafeRunSync()
    sched
  }
}
