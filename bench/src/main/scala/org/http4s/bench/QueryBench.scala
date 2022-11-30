package org.http4s.bench

import org.http4s.Query
import org.http4s.Query.KeyValue
import org.openjdk.jmh.annotations._

import java.util.UUID
import java.util.concurrent.TimeUnit

@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
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
}
