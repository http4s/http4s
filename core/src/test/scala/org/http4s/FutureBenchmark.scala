package org.http4s

import org.scalameter.PerformanceTest
import org.scalameter.api._
import org.scalameter.PerformanceTest
import scala.concurrent.{Await, Future}
import scalaz.concurrent.Task
import scala.concurrent.duration._

object FutureBenchmark extends PerformanceTest.Quickbenchmark {
  val opses: Gen[Int] = Gen.enumeration("ops")(1, 10, 1000, 10000, 100000)
  val concurrencies: Gen[Int] = Gen.enumeration("concurrency")(1, 4, 16, 64)
  val params = Gen.tupled(concurrencies, opses)

  import scala.concurrent.ExecutionContext.Implicits.global

  performance of "scala.concurrent.Future" in {
    using(params) config (
      exec.benchRuns -> 100,
      exec.minWarmupRuns -> 100
    ) in { case (concurrency, ops) =>
      val futures = (0 to concurrency).map { _ =>
        var f = Future(0)
        for (i <- 1 to ops) { f = f.map(_ + 1) }
        f
      }
      Await.result(Future.sequence(futures), 1.minute)
    }
  }

  performance of "scalaz.concurrent.Task" in {
    using(params) config (
      exec.benchRuns -> 100,
      exec.minWarmupRuns -> 100
    ) in { case (concurrency, ops) =>
      val tasks = (0 to concurrency).map { _ =>
        var t = Task(0)
        for (i <- 1 to ops) { t = t.map(_ + 1) }
        t
      }
      Task.gatherUnordered(tasks).run
    }
  }
}
