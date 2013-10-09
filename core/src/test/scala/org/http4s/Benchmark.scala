package org.http4s

import scalaz._, std.indexedSeq._, std.option._, syntax.traverse._
import scalaz.concurrent.Task
import scalaz.stream._, Process._
import play.api.libs.iteratee.{Iteratee, Enumerator}
import scala.concurrent.{Future}
import scala.concurrent.duration.Duration

object Benchmark {
  def time[A](f: => A): (A, Long) = {
    val start = System.nanoTime()
    (f, System.nanoTime() - start)
  }

  import scala.concurrent.ExecutionContext.Implicits.global

  def iteratee(concurrent: Int, size: Int) = {
    time {
      scala.concurrent.Await.result(
        Future.sequence(
          for (_ <- 0 until concurrent) yield Future(Enumerator.enumerate(0 until size).run(Iteratee.fold(0){ _ + _}))),
        Duration.Inf)
    }
  }

  def task(concurrent: Int, size: Int) = {
    time {
      Task.gatherUnordered(for (_ <- 0 until concurrent) yield
        Task.fork(Process.suspend(Process.emitSeq(0 until size)).fold(0){ _ + _ }.runLastOr(0))).run
    }
  }

  def ratio(concurrent: Int, size: Int) = task(concurrent, size)._2.toDouble / iteratee(concurrent, size)._2
}
