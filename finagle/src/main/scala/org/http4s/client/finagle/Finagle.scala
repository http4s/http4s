package org.http4s
package finagle

import client._
import cats.effect._
import cats.syntax.functor._
import com.twitter.finagle.{Http, Service}
import com.twitter.finagle.http.{Request => Req, Response => Resp, Method, RequestBuilder}
import com.twitter.util.{Future, Return, Throw}
import com.twitter.io._
import cats.syntax.flatMap._
import fs2.{Chunk, Stream}
import cats.Functor
import com.twitter.util.Promise

object Finagle {

  def mkClient[F[_]](dest: String)(implicit F: ConcurrentEffect[F]): Resource[F, Client[F]] =
    mkClient(Http.newService(dest))

  def mkClient[F[_]](svc: Service[Req, Resp])(
      implicit F: ConcurrentEffect[F]): Resource[F, Client[F]] =
    Resource
      .make(F.delay(svc)) { _ =>
        F.delay(())
      }
      .flatMap(svc => Resource.liftF(allocate(svc)))
  def mkService[F[_]: Functor: ConcurrentEffect](route: HttpApp[F]): Service[Req, Resp] =
    new Service[Req, Resp] {
      def apply(req: Req) =
        toFuture(route.local(fromFinagleReq[F]).flatMapF(toFinagleResp[F]).run(req))
    }

  private def allocate[F[_]](svc: Service[Req, Resp])(
      implicit F: ConcurrentEffect[F]): F[Client[F]] =
    F.delay(Client[F] { req =>
      Resource
        .liftF(for {
          freq <- toFinagleReq(req)
          resp <- toF(svc(freq))
        } yield resp)
        .map(toHttp4sResp)
    })

  private def fromFinagleReq[F[_]](req: Req): Request[F] = {
    import org.http4s.{Header, Method}
    val method = Method.fromString(req.method.name).getOrElse(Method.GET)
    val uri = Uri.unsafeFromString(req.uri)
    val headers = Headers(req.headerMap.toList.map { case (name, value) => Header(name, value) })
    val body = toStream[F](req.content)
    val version = HttpVersion
      .fromVersion(req.version.major, req.version.minor)
      .getOrElse(HttpVersion.`HTTP/1.1`)
    Request(method, uri, version, headers, body)
  }

  private def toFinagleResp[F[_]: Concurrent](resp: Response[F]): F[Resp] = {
    import com.twitter.finagle.http.{Status}
    val status = Status(resp.status.code)
    val headers = resp.headers.toList.map(h => (h.name.toString, h.value))
    val finagleResp = Resp(status)
    finagleResp.headerMap.addAll(headers)
    val writeBody = if (resp.isChunked) {
      finagleResp.setChunked(true)
      Concurrent[F].start(streamBody(resp.body, finagleResp.writer).compile.drain).void
    } else {
      resp
        .as[Array[Byte]]
        .map { Buf.ByteArray.Owned(_) }
        .map(finagleResp.content = _)
        .void
    }
    writeBody.as(finagleResp)
  }

  private def toFinagleReq[F[_]](req: Request[F])(implicit F: Concurrent[F]): F[Req] = {
    val method = Method(req.method.name)
    val reqheaders = req.headers.toList.map(h => (h.name.toString, h.value)).toMap
    val reqBuilder = RequestBuilder().url(req.uri.toString).addHeaders(reqheaders)
    if (req.isChunked) {
      val request = reqBuilder.build(method, None)
      request.headerMap.remove("Transfer-Encoding")
      request.setChunked(true)
      Concurrent[F].start(streamBody(req.body, request.writer).compile.drain).as(request)
    } else {
      req.as[Array[Byte]].map { b =>
        val body = if (b.isEmpty) None else Some(Buf.ByteArray.Owned(b))
        reqBuilder.build(method, body)
      }
    }
  }

  private def streamBody[F[_]: Async](
      body: Stream[F, Byte],
      writer: Writer[Buf]): Stream[F, Unit] = {
    import com.twitter.finagle.http.{Chunk}
    (body.chunks.map(a => Chunk.fromByteArray(a.toArray).content).evalMap { a =>
      toF(writer.write(a))
    }) ++ Stream.eval { toF(writer.close()) }
  }

  private def toStream[F[_]](buf: Buf): Stream[F, Byte] =
    Stream.chunk[F, Byte](Chunk.array(Buf.ByteArray.Owned.extract(buf)))

  private def toHttp4sResp[F[_]](resp: Resp): Response[F] =
    Response[F](
      status = Status(resp.status.code)
    ).withHeaders(Headers(resp.headerMap.toList.map { case (name, value) => Header(name, value) }))
      .withEntity(toStream[F](resp.content))

  private def toF[F[_], A](f: Future[A])(implicit F: Async[F]): F[A] = F.async { cb =>
    f.respond {
      case Return(value) => cb(Right(value))
      case Throw(exception) => cb(Left(exception))
    }
    ()
  }
  private def toFuture[F[_]: Effect, A](f: F[A]): Future[A] = {
    val promise: Promise[A] = Promise()
    Effect[F]
      .runAsync(f) {
        case Right(value) => IO(promise.setValue(value))
        case Left(exception) => IO(promise.setException(exception))
      }
      .unsafeRunSync()
    promise
  }
}
