/*
 * Copyright 2015 http4s.org
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

package org.http4s.bench

import org.http4s.Query
import org.http4s.bench.input.QueryParamInput
import org.http4s.internal.CollectionCompat
import org.openjdk.jmh.annotations._

import java.util.concurrent.TimeUnit

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
class QueryParamBench {
  @Benchmark def withQueryParam(in: QueryParamInput): Query =
    in.queryParams.foldLeft(Query.empty) { case (query, (key, value)) =>
      query.withQueryParam(key, value)
    }

  @Benchmark def setQueryParams(in: QueryParamInput): Query =
    Query.empty.setQueryParams(CollectionCompat.mapValues(in.queryParams)(Seq(_)))
}
