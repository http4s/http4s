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
import cats.effect.Resource
import cats.effect.unsafe.IORuntime
import cats.effect.unsafe.IORuntimeConfig
import cats.effect.unsafe.Scheduler
import org.http4s.internal.threads.newBlockingPool
import org.http4s.internal.threads.newDaemonPool
import org.http4s.internal.threads.threadFactory

import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext

trait Http4sSuitePlatform { this: Http4sSuite =>

  def resourceSuiteFixture[A](name: String, resource: Resource[IO, A]) = registerSuiteFixture(
    ResourceSuiteLocalFixture(name, resource)
  )

  // allow flaky tests on ci
  override def munitFlakyOK = sys.env.contains("CI")
}

trait Http4sSuiteCompanionPlatform {

  val TestScheduler: ScheduledExecutorService = {
    val s =
      new ScheduledThreadPoolExecutor(2, threadFactory(i => s"http4s-test-scheduler-$i", true))
    s.setKeepAliveTime(10L, TimeUnit.SECONDS)
    s.allowCoreThreadTimeOut(true)
    s
  }

  val TestIORuntime: IORuntime = {
    val blockingPool = newBlockingPool("http4s-suite-blocking")
    val computePool = newDaemonPool("http4s-suite", timeout = true)
    val scheduledExecutor = TestScheduler
    IORuntime.apply(
      ExecutionContext.fromExecutor(computePool),
      ExecutionContext.fromExecutor(blockingPool),
      Scheduler.fromScheduledExecutor(scheduledExecutor),
      () => {
        blockingPool.shutdown()
        computePool.shutdown()
        scheduledExecutor.shutdown()
      },
      IORuntimeConfig(),
    )
  }
}
