package org.http4s

import org.scalameter.api._

object HttpHeadersBenchmark extends PerformanceTest.Quickbenchmark {
  val sizes: Gen[Int] = Gen.enumeration("size")(2, 4, 8, 16, 32, 64)

  val headerses: Gen[Seq[HttpHeader]] = for {
    size <- sizes
  } yield for {
    i <- 0 until size
  } yield HttpHeaders.RawHeader("X-HttpHeaders-Benchmark-"+i, i.toString)


  performance of "HttpHeaders" in {
    measure method "apply" config {
      exec.benchRuns -> 100000
      exec.minWarmupRuns -> 100000
    } in {
      using (headerses) in {
        headers => HttpHeaders.apply(headers: _*)
      }
    }
  }
}
