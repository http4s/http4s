package org.http4s

import org.scalameter.PerformanceTest
import org.scalameter.api._
import org.scalameter.PerformanceTest
import scala.concurrent.{ExecutionContext, Await, Future}
import scalaz.concurrent.Task
import scala.concurrent.duration._
import java.util.{ArrayDeque, Deque}

object FutureBenchmark extends PerformanceTest.Quickbenchmark {
  val opses: Gen[Int] = Gen.enumeration("ops")(1, 10, 1000, 10000, 100000)
  val concurrencies: Gen[Int] = Gen.enumeration("concurrency")(1, 4, 16, 64)
  val params = Gen.tupled(concurrencies, opses)

  def doFuture(concurrency: Int, ops: Int)(implicit ec: ExecutionContext) {
    val futures = (0 to concurrency).map { _ =>
      var f = Future(0)
      for (i <- 1 to ops) { f = f.map(_ + 1) }
      f
    }
    Await.result(Future.sequence(futures), 1.minute)
  }

  def doTask(concurrency: Int, ops: Int) {
    val tasks = (0 to concurrency).map { _ =>
      Task.fork {
        var t = Task(0)
        for (i <- 1 to ops) { t = t.map(_ + 1) }
        t
      }
    }
    Task.fork(Task.gatherUnordered(tasks)).run
  }

  def bench(f: (Int, Int) => Unit) {
    using(params) config (
      exec.benchRuns -> 2000,
      exec.minWarmupRuns -> 200
    ) in { case (concurrency, ops) => f(concurrency, ops) }
  }

  performance of "scala.concurrent.Future with global EC" in {
    import scala.concurrent.ExecutionContext.Implicits.global
    bench(doFuture _)
  }

  /*
  performance of "scala.concurrent.Future with EC with affinity" in {
    implicit val trampoline: ExecutionContext = new ExecutionContext {
      private val local = new ThreadLocal[Deque[Runnable]]
      def execute(runnable: Runnable): Unit = {
        var queue = local.get()
        if (queue == null) {
          // Since there is no local queue, we need to install one and
          // start our trampolining loop.
          try {
            queue = new ArrayDeque(4)
            queue.addLast(runnable)
            local.set(queue)
            while (!queue.isEmpty) {
              val runnable = queue.removeFirst()
              runnable.run()
            }
          } finally {
            // We've emptied the queue, so tidy up.
            local.set(null)
          }
        } else {
          // There's already a local queue that is being executed.
          // Just stick our runnable on the end of that queue.
          queue.addLast(runnable)
        }
      }
      def reportFailure(t: Throwable): Unit = t.printStackTrace()
    }
    bench(doFuture _)
  }
  */

  performance of "scalaz.concurrent.Task" in {
    bench(doTask _)
  }
}
