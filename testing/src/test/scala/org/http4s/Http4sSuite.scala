/*
 * Copyright 2016 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.syntax.all._
import cats.effect.unsafe.Scheduler
import fs2._
import fs2.text.utf8Decode
import java.util.concurrent.{ScheduledExecutorService, ScheduledThreadPoolExecutor, TimeUnit}
import munit._
import org.http4s.internal.threads.{newBlockingPool, newDaemonPool, threadFactory}
import scala.concurrent.ExecutionContext

/** Common stack for http4s' munit based tests
  */
trait Http4sSuite extends CatsEffectSuite with DisciplineSuite with munit.ScalaCheckEffectSuite {
  override implicit val ioRuntime: IORuntime = Http4sSuite.TestIORuntime

  implicit class ParseResultSyntax[A](self: ParseResult[A]) {
    def yolo: A = self.valueOr(e => sys.error(e.toString))
  }

  def writeToString[A](a: A)(implicit W: EntityEncoder[IO, A]): IO[String] =
    Stream
      .emit(W.toEntity(a))
      .covary[IO]
      .flatMap(_.body)
      .through(utf8Decode)
      .foldMonoid
      .compile
      .last
      .map(_.getOrElse(""))

}

object Http4sSuite {
  val TestScheduler: ScheduledExecutorService = {
    val s =
      new ScheduledThreadPoolExecutor(2, threadFactory(i => s"http4s-test-scheduler-$i", true))
    s.setKeepAliveTime(10L, TimeUnit.SECONDS)
    s.allowCoreThreadTimeOut(true)
    s
  }

  val TestIORuntime: IORuntime = {
    val blockingPool = newBlockingPool("http4s-spec-blocking")
    val computePool = newDaemonPool("http4s-spec", timeout = true)
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
