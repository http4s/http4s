package org.http4s
package finagle

import client._
import cats.effect._
import cats.syntax.functor._
import com.twitter.finagle.{Http,Service}
import com.twitter.finagle.http.{Request => Req, Response=>Resp, Method, RequestBuilder}
import com.twitter.util.{Return, Throw, Future}
import com.twitter.io._
import cats.syntax.flatMap._
import fs2.{
  Chunk, Stream
}
import cats.Functor
import com.twitter.util.Promise

object Finagle {

  def mkClient[F[_]](dest: String)(
    implicit F: ConcurrentEffect[F]): Resource[F, Client[F]] = mkResource(Http.newService(dest))

  def mkService[F[_]:Functor: Effect:ConcurrentEffect](route: HttpApp[F]): Service[Req,Resp] = new Service[Req,Resp] {
    def apply(req: Req) = toFuture(route.local(fromFinagleReq[F]).flatMapF(toFinagleResp[F]).run(req))
  }

  private def allocate[F[_]](svc: Service[Req, Resp])(
      implicit F: ConcurrentEffect[F]): F[Client[F]] =
       F.delay(Client[F] { req =>
         Resource.liftF(for{
           freq <- toFinagleReq(req)
           resp <- toF(svc(freq))
         }yield resp).map(toHttp4sResp)
        })

  private def mkResource[F[_]](svc: Service[Req, Resp])(
    implicit F: ConcurrentEffect[F]): Resource[F, Client[F]] = {
    Resource.make(F.delay(svc)){_ => F.delay(())}
    .flatMap(svc => Resource.liftF(allocate(svc)))
  }
  private def fromFinagleReq[F[_]](req: Req): Request[F] = {
    val method: org.http4s.Method = org.http4s.Method.fromString(req.method.name).getOrElse(org.http4s.Method.GET)
    val uri = Uri.unsafeFromString(req.uri)
    val headers = Headers(req.headerMap.toList.map{case (name, value) => org.http4s.Header(name, value)})
    val body = toStream[F](req.content)
    Request(method, uri, HttpVersion.`HTTP/1.1`, headers, body)
  }

  private def toFinagleResp[F[_]:ConcurrentEffect](resp: Response[F]): F[Resp] = {
    import com.twitter.finagle.http.{Status}
    val status = Status(resp.status.code)
    val headers = resp.headers.toList.map(h => (h.name.toString, h.value))
    val finagleResp = Resp(status)
    finagleResp.headerMap.addAll(headers)
    val bodyUpdate = resp.body.chunks.map(_.toArray).evalMap{ a=>
      val out = (finagleResp.writer.write(com.twitter.finagle.http.Chunk.fromByteArray(a).content))
      toF(out)
    }.compile.drain.map(_ => toF((finagleResp.writer.close())))
    ConcurrentEffect[F].runCancelable(bodyUpdate)(_=>IO.unit).to[F].as(finagleResp)
  }

  private def toFinagleReq[F[_]](req: Request[F])(implicit F: ConcurrentEffect[F]):F[Req] = {
    val method = Method(req.method.name)
    val reqheaders = req.headers.toList.map(h => (h.name.toString, h.value)).toMap
    val reqBuilder = RequestBuilder().url(req.uri.toString).addHeaders(reqheaders)
    if (req.isChunked) {
      val request = reqBuilder.build(method, None)
        request.headerMap.remove("Transfer-Encoding")
        val writer = request.chunkWriter
        request.setChunked(true)
        val bodyUpdate = req.body.chunks.map(_.toArray).evalMap{ a=>
          val out = (writer.write(com.twitter.finagle.http.Chunk.fromByteArray(a)))
          toF(out)
        }.compile.drain.map(_ => toF((writer.close())))
        ConcurrentEffect[F].runCancelable(bodyUpdate)(_=>IO.unit).to[F].as(request)
    }else{
        req.as[Array[Byte]].map{b=>
          val body = if (b.isEmpty) None else Some(Buf.ByteArray.Owned(b))
          reqBuilder.build(method, body)
        }
    }
  }

  private def toStream[F[_]](buf: Buf): Stream[F, Byte] = {
      Stream.chunk[F, Byte](Chunk.array(Buf.ByteArray.Owned.extract(buf)))
  }

  private def toHttp4sResp[F[_]](resp: Resp): Response[F] = {
    val response = Response[F](
      status = Status(resp.status.code)
    ).withHeaders(Headers(resp.headerMap.toList.map{case (name, value) => Header(name, value)}))
      .withEntity(toStream[F](resp.content))
    response
  }

  private def toF[F[_], A](f: Future[A])(implicit F: Async[F]): F[A] = F.async{cb=>
    f.respond{
      case Return(value) => cb(Right(value))
      case Throw(exception) => cb(Left(exception))
    }
    ()
  }
  private def toFuture[F[_]: Effect, A](f: F[A]): Future[A] = {
    val promise: Promise[A] = Promise()
    Effect[F].runAsync(f) {
      case Right(value)    => IO(promise.setValue(value))
      case Left(exception) => IO(promise.setException(exception))
    }.unsafeRunSync()
    promise
  }
}
