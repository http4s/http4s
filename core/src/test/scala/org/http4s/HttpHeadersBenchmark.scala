package org.http4s

import org.scalameter.api._

object HttpHeadersBenchmark extends PerformanceTest.Quickbenchmark {
  val sizes: Gen[Int] = Gen.enumeration("size")(2, 4, 8, 16, 32, 64)

  val headerses: Gen[Seq[HttpHeader]] = for {
    size <- sizes
  } yield for {
    i <- 0 until size
  } yield HttpHeaders.RawHeader("X-HttpHeaders-Benchmark-"+i, i.toString)

  val replacements: Gen[(HeaderCollection, HttpHeader)] = for {
    headers <- headerses
  } yield {
    val i = headers.size / 2
    val header = HttpHeaders.RawHeader("X-HttpHeaders-Benchmark-"+i, "replacement")
    (HeaderCollection.apply(headers: _*), header)
  }

  performance of "HttpHeaders" in {
    measure method "apply" config (
      exec.benchRuns -> 500000,
      exec.minWarmupRuns -> 100000
    ) in {
      using (headerses) in {
        headers => HeaderCollection.apply(headers: _*)
      }
    }

    measure method ":+=" config (
      exec.benchRuns -> 500000,
      exec.minWarmupRuns -> 100000
    ) in {
      using (headerses) in { headers =>
        var target: HeaderCollection = HeaderCollection.empty
        for (header <- headers) {
          target :+= header
        }
      }
    }

    measure method "replace"  in {
      using (replacements) config (
        exec.benchRuns -> 500000,
        exec.minWarmupRuns -> 100000
      ) in { case (headers, replacement) =>
        headers.put(replacement)
      }
    }
  }
}
