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
import scalaz.effect.IO

trait ServletDriver[F[_]] {
  def requestBody(request: HttpServletRequest, chunkSize: Int): HttpBody[F]

  def responseBodySink(response: HttpServletResponse): Sink[F, Array[Byte]]

  def run(servletRequest: HttpServletRequest, servletResponse: HttpServletResponse, process: Process[F, Unit]): Unit
}

object ServletDriver {
  implicit def futureDriver(implicit ec: ExecutionContext = ExecutionContext.global) = new ServletDriver[Future] {
    def requestBody(request: HttpServletRequest, chunkSize: Int): HttpBody[Future] =
      chunkR(request.getInputStream).map(f => f(chunkSize).map(BodyChunk.apply _)).eval

    def responseBodySink(response: HttpServletResponse): Sink[Future, Array[Byte]] = chunkW(response.getOutputStream)

    def run(servletRequest: HttpServletRequest, servletResponse: HttpServletResponse, process: Process[Future, Unit]) {
      val ctx = servletRequest.startAsync()
      process.run.onComplete(_ => ctx.complete())
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

    def suspend[A](p: => Process[Future, A]): Process[Future, A] = await(Future())(_ => p)
  }

  implicit val TaskDriver = new ServletDriver[Task] {
    def requestBody(request: HttpServletRequest, chunkSize: Int): HttpBody[Task] =
      chunkR(request.getInputStream).map(f => f(chunkSize).map(BodyChunk.apply _)).eval

    def responseBodySink(response: HttpServletResponse): Sink[Task, Array[Byte]] = chunkW(response.getOutputStream)

    def run(servletRequest: HttpServletRequest, servletResponse: HttpServletResponse, process: Process[Task, Unit]) {
      val ctx = servletRequest.startAsync()
      process.run.runAsync(_ => ctx.complete())
    }
  }

  // Don't get too attached.  This is a copy'n'pasted, ill-conceived rush job.
  implicit val IoDriver = new ServletDriver[IO] {
    def requestBody(request: HttpServletRequest, chunkSize: Int): HttpBody[IO] =
      chunkR(request.getInputStream).map(f => f(chunkSize).map(BodyChunk.apply _)).eval

    def responseBodySink(response: HttpServletResponse): Sink[IO, Array[Byte]] = chunkW(response.getOutputStream)

    def run(servletRequest: HttpServletRequest, servletResponse: HttpServletResponse, process: Process[IO, Unit]) {
      process.run
    }

    // Hacking... copy parts of scalaz-stream, replace Task with Future
    def chunkR(is: => InputStream): Channel[IO, Int, Array[Byte]] =
      unsafeChunkR(is).map(f => (n: Int) => {
        val buf = new Array[Byte](n)
        f(buf).map(_.toArray)
      })

    def unsafeChunkR(is: => InputStream): Channel[IO,Array[Byte],Bytes] = {
      resource(IO(is))(
        src => IO(src.close)) { src =>
        IO { (buf: Array[Byte]) => IO {
          val m = src.read(buf)
          if (m == -1) throw End
          else new Bytes(buf, m)
        }}
      }
    }

    def chunkW(os: => OutputStream): Process[IO, Array[Byte] => IO[Unit]] =
      resource(IO(os))(os => IO(os.close))(
        os => IO((bytes: Array[Byte]) => IO(os.write(bytes))))

    private def resource[R,O](acquire: IO[R])(release: R => IO[Unit])(step: R => IO[O]): Process[IO,O] = {
      def go(step: IO[O], onExit: Process[IO,O]): Process[IO,O] =
        await[IO,O,O](step) (
          o => emit(o) ++ go(step, onExit) // Emit the value and repeat
          , onExit                           // Release resource when exhausted
          , onExit)                          // or in event of error
      await(acquire)(r => {
        val onExit = suspend(eval(release(r)).drain)
        go(step(r), onExit)
      }, halt, halt)
    }

    def suspend[A](p: => Process[IO, A]): Process[IO, A] = await(IO())(_ => p)
  }
}


