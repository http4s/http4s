/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.bench

import java.util.concurrent.TimeUnit
import org.http4s.Query
import org.http4s.bench.input.QueryParamInput
import org.http4s.internal.CollectionCompat
import org.openjdk.jmh.annotations._

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
class QueryParamBench {
  @Benchmark def withQueryParam(in: QueryParamInput): Query =
    in.queryParams.foldLeft(Query.empty) {
      case (query, (key, value)) => query.withQueryParam(key, value)
    }

  @Benchmark def setQueryParams(in: QueryParamInput): Query =
    Query.empty.setQueryParams(CollectionCompat.mapValues(in.queryParams)(Seq(_)))
}
