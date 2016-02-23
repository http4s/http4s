package org.http4s.bench

import java.util.concurrent.TimeUnit

import org.http4s.{QueryOps, Query}
import org.http4s.bench.input.QueryParamInput
import org.openjdk.jmh.annotations._

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
class QueryParamBench {

  @Benchmark def withQueryParam(in: QueryParamInput): QueryOps =
    in.queryParams.foldLeft[QueryOps](Query.empty){ case (query, (key, value)) => query.withQueryParam(key, value) }

  @Benchmark def setQueryParams(in: QueryParamInput): QueryOps =
    Query.empty.setQueryParams(in.queryParams.mapValues(Seq(_)))

}
