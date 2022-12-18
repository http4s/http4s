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
import org.http4s.Query.KeyValue
import org.http4s.QueryParam
import org.http4s.QueryParamEncoder
import org.openjdk.jmh.annotations._

import java.util.UUID
import java.util.concurrent.TimeUnit

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
class QueryBench {
  @Param(Array("0", "10", "100", "1000"))
  var size: Int = _

  var rawData: Seq[(String, Option[String])] = _
  var parsedQuery: Query = _
  var rawQuery: Query = _

  def generateKeyValue(): KeyValue =
    UUID.randomUUID().toString -> Option(UUID.randomUUID().toString)

  @Setup(Level.Trial)
  def setup(): Unit = {
    rawData = (0 to size).map(_ => generateKeyValue())
    parsedQuery = Query(rawData: _*)
    rawQuery = Query.parser
      .parse(rawData.map(x => s"${x._1}=${x._2.get}").mkString(";"))
      .map(_._2)
      .toOption
      .get
  }

  @Benchmark
  def parseQuery: Query =
    Query.unsafeFromString(rawData.map(x => s"${x._1}=${x._2.get}").mkString(";"))

  @Benchmark
  def sliceHalfParsedQuery: Query =
    parsedQuery.slice(0, size / 2 + 1)

  @Benchmark
  def sliceHalfRawQuery: Query =
    rawQuery.slice(0, size / 2 + 1)

  @Benchmark
  def filterParsedQuery: Query =
    parsedQuery.filter(_._2.hashCode() % 2 == 0)

  @Benchmark
  def filterRawQuery: Query =
    rawQuery.filter(_._2.hashCode() % 2 == 0)

  @Benchmark
  def isEmptyParsedQuery: Boolean =
    parsedQuery.isEmpty

  @Benchmark
  def isEmptyRawQuery: Boolean =
    rawQuery.isEmpty

  @Benchmark
  def prependParsedQuery: Query =
    generateKeyValue() +: parsedQuery

  @Benchmark
  def prependRawQuery: Query =
    generateKeyValue() +: rawQuery

  final case class Key(key: String)

  object Key {
    implicit val queryParam: QueryParam[Key] =
      QueryParam.fromKey("foo")
  }

  final case class Value(value: String)

  object Value {
    implicit val queryParamEncoder: QueryParamEncoder[Value] =
      QueryParamEncoder[String].contramap(_.value)
  }

  @Benchmark
  def withQueryParamRawQuery: Query =
    rawQuery.withQueryParam[Value, Key](Key("foo"), Value("bar"))

  @Benchmark
  def withQueryParamParsedQuery: Query =
    parsedQuery.withQueryParam[Value, Key](Key("foo"), Value("bar"))
}
