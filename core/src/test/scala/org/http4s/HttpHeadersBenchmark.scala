package org.http4s

import org.scalameter.api._
import org.scalameter.picklers.Implicits._

object HttpHeadersBenchmark extends Bench.LocalTime {
  val sizes: Gen[Int] = Gen.enumeration("size")(2, 4, 8, 16, 32, 64)

  val headerses: Gen[Seq[Header]] = for {
    size <- sizes
  } yield for {
    i <- 0 until size
  } yield Header("X-Headers-Benchmark-"+i, i.toString)

  val replacements: Gen[(Headers, Header)] = for {
    headers <- headerses
  } yield {
    val i = headers.size / 2
    val header = Header("X-Headers-Benchmark-"+i, "replacement")
    (Headers.apply(headers: _*), header)
  }

  performance of "Headers" in {
    measure method "apply" config (
      exec.benchRuns -> 500000,
      exec.minWarmupRuns -> 100000
    ) in {
      using (headerses) in {
        headers => Headers.apply(headers: _*)
      }
    }

    measure method ":+=" config (
      exec.benchRuns -> 500000,
      exec.minWarmupRuns -> 100000
    ) in {
      using (headerses) in { headers =>
        var target: Headers = Headers.empty
        for (header <- headers) {
          target = target.put(header)
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
