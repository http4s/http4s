package org.http4s
package servlet

import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import scala.concurrent.{Future, ExecutionContext}
import scalaz.concurrent.Task
import scalaz.stream.io._
import scalaz.stream.Process._
import javax.servlet.AsyncContext
import scalaz.\/
import scalaz.stream.{Bytes, Process}
import java.io.{InputStream, OutputStream}

trait ServletDriver[F[_]] {
  def requestBody(request: HttpServletRequest, chunkSize: Int): HttpBody[F]

  def responseBodySink(response: HttpServletResponse): Sink[F, Array[Byte]]

  def runAsync(f: F[Unit], callback: => Unit): Unit
}

object ServletDriver {
  implicit def futureDriver(implicit ec: ExecutionContext = ExecutionContext.global) = new ServletDriver[Future] {
    def requestBody(request: HttpServletRequest, chunkSize: Int): HttpBody[Future] =
      chunkR(request.getInputStream).map(f => f(chunkSize).map(BodyChunk.apply _)).eval

    def responseBodySink(response: HttpServletResponse): Sink[Future, Array[Byte]] = chunkW(response.getOutputStream)

    def runAsync(f: Future[Unit], callback: => Unit) {
      f.onComplete(_ => callback)
    }

    // Hacking... copy parts of scalaz-stream, replace Task with Future
    def chunkR(is: => InputStream): Channel[Future, Int, Array[Byte]] =
      unsafeChunkR(is).map(f => (n: Int) => {
        val buf = new Array[Byte](n)
        f(buf).map(_.toArray)
      })

    def unsafeChunkR(is: => InputStream): Channel[Future,Array[Byte],Bytes] = {
      resource(Future(is))(
        src => Future(src.close)) { src =>
        Future { (buf: Array[Byte]) => Future {
          val m = src.read(buf)
          if (m == -1) throw End
          else new Bytes(buf, m)
        }}
      }
    }

    def chunkW(os: => OutputStream): Process[Future, Array[Byte] => Future[Unit]] =
      resource(Future(os))(os => Future(os.close))(
        os => Future((bytes: Array[Byte]) => Future(os.write(bytes))))

    private def resource[R,O](acquire: Future[R])(release: R => Future[Unit])(step: R => Future[O]): Process[Future,O] = {
      def go(step: Future[O], onExit: Process[Future,O]): Process[Future,O] =
        await[Future,O,O](step) (
          o => emit(o) ++ go(step, onExit) // Emit the value and repeat
          , onExit                           // Release resource when exhausted
          , onExit)                          // or in event of error
      await(acquire)(r => {
        val onExit = suspend(eval(release(r)).drain)
        go(step(r), onExit)
      }, halt, halt)
    }

    // Hacking... copy parts of scalaz-stream, replace Task with Future
    def suspend[A](p: => Process[Future, A]): Process[Future, A] = await(Future())(_ => p)
  }

  implicit val TaskDriver = new ServletDriver[Task] {
    def requestBody(request: HttpServletRequest, chunkSize: Int): HttpBody[Task] =
      chunkR(request.getInputStream).map(f => f(chunkSize).map(BodyChunk.apply _)).eval

    def responseBodySink(response: HttpServletResponse): Sink[Task, Array[Byte]] = chunkW(response.getOutputStream)

    def runAsync(f: Task[Unit], callback: => Unit) {
      f.runAsync(_ => callback)
    }
  }
}


