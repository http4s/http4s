package org.http4s.bench

import org.http4s._
import org.openjdk.jmh.annotations._

import java.util.concurrent.TimeUnit

@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
class HttpVersionEncodeBench {
  import HttpVersionEncodeBench._
  import org.http4s.util._

  @Benchmark
  def renderNew: String = {
    val sw = new StringWriter()
    http1Render.render(sw, HttpVersion.`HTTP/1.1`)
    sw.result
  }

  @Benchmark
  def renderOld: String = {
    val sw = new StringWriter()
    http1RenderOld.render(sw, HttpVersion.`HTTP/1.1`)
    sw.result
  }

  @Benchmark
  def renderList: String = {
    val sw = new StringWriter()
    http1RenderList.render(sw, HttpVersionEncodeBench.list)
    sw.result
  }

  @Benchmark
  def renderListOld: String = {
    val sw = new StringWriter()
    http1RenderListOld.render(sw, HttpVersionEncodeBench.list)
    sw.result
  }
}

object HttpVersionEncodeBench {
  import org.http4s.codec._

  val http1Render = HttpVersion.http1Codec.foldMap(rendererEncoder)
  val http1RenderList = Http1Codec.listOf(HttpVersion.http1Codec).foldMap(rendererEncoder)

  val list = List.fill(100)(HttpVersion.`HTTP/1.1`)

  val http1RenderOld: org.http4s.util.Renderer[HttpVersion] =
    new org.http4s.util.Renderer[HttpVersion] {
      def render(writer: org.http4s.util.Writer, v: HttpVersion): writer.type =
        writer << "HTTP/" << v.major << '.' << v.minor
    }
  val http1RenderListOld: org.http4s.util.Renderer[List[HttpVersion]] =
    org.http4s.util.Renderer.listRenderer(http1RenderOld)
}
