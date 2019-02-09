package org.http4s
package bench

import java.util.concurrent.TimeUnit

import cats.effect.IO
import io.circe._
import io.circe.parser._
import org.http4s.circe._
import org.openjdk.jmh.annotations._

// sbt "bench/jmh:run -i 10 -wi 10 -f 2 -t 1 org.http4s.bench.CirceJsonBench"
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
class CirceJsonBench {
  import CirceJsonBench._

  @Benchmark
  def decode_byte_buffer(in: BenchState): Either[DecodeFailure, Json] =
    jsonDecoderByteBuffer[IO].decode(in.req, strict = true).value.unsafeRunSync

  @Benchmark
  def decode_incremental(in: BenchState): Either[DecodeFailure, Json] =
    jsonDecoderIncremental[IO].decode(in.req, strict = true).value.unsafeRunSync

  @Benchmark
  def decode_adaptive(in: BenchState): Either[DecodeFailure, Json] =
    jsonDecoderAdaptive[IO](in.cutoff, MediaType.application.json)
      .decode(in.req, strict = true)
      .value
      .unsafeRunSync
}

object CirceJsonBench {
  // val obj = Json.obj("foo" -> Json.obj("foo2" -> Json.fromString("bar")))
  val jsonStr =
    """{"_id":"58fefc19a5eab74376285220","index":0,"guid":"f5740e2b-7bcf-4bb8-9303-774b1ad99f84","isActive":false,"balance":"$1,052.47","picture":"http://placehold.it/32x32","age":34,"eyeColor":"brown","name":"Calhoun Schneider","gender":"male","company":"GEEKMOSIS","email":"calhounschneider@geekmosis.com","phone":"+1 (890) 564-2582","address":"457 Milford Street, Cutter, Nevada, 204","about":"Irure esse sint esse ullamco dolor veniam commodo eiusmod fugiat minim nostrud. Minim occaecat aliquip magna qui ad nisi laboris adipisicing eiusmod amet deserunt ut. Commodo nulla ullamco et esse eu fugiat elit amet nisi dolore id id aliquip qui. In ex excepteur ea labore nulla eu ullamco anim occaecat sint. Consequat do excepteur consequat est.\r\n","registered":"2014-12-04T02:58:51 -06:00","latitude":60.317658,"longitude":-58.313338,"tags":["laboris","ad","ullamco","ea","ex","est","labore"],"friends":[{"id":0,"name":"Angie Johns"},{"id":1,"name":"Francine Mclean"},{"id":2,"name":"Elaine Hebert"}],"greeting":"Hello, Calhoun Schneider! You have 9 unread messages.","favoriteFruit":"apple"}"""
  val obj = parse(jsonStr).right.get

  @State(Scope.Benchmark)
  class BenchState {
    @Param(Array("1100", "5000", "10000", "100000", "500000"))
    var approxContentLength: Int = _
    @Param(Array("100000"))
    var cutoff: Long = _
    var req: Request[IO] = _

    @Setup(Level.Trial)
    def setup(): Unit = {
      val arraySize = (approxContentLength.toDouble /
        jsonStr.getBytes("UTF-8").length.toDouble).round.toInt
      val json = Json.arr((1 to arraySize).map(_ => obj): _*)
      req = Request[IO]().withEntity(json)
      println(
        s"Array size: $arraySize; Approx. Content-Length: $approxContentLength; Content-Length: ${req.contentLength}")
    }
  }
}
