package com.example.http4s

import org.http4s._
import org.http4s.dsl._
import org.http4s.server.HttpService
import org.http4s.scalaxml._
import scodec.bits.ByteVector

import scalaz.{Reducer, Monoid}
import scalaz.concurrent.Task
import scalaz.stream.Process
import scalaz.stream.Process._

/** These are routes that we tend to use for testing purposes
  * and will likely get folded into unit tests later in life */
object ScienceExperiments {

  val flatBigString = (0 until 1000).map{ i => s"This is string number $i" }.foldLeft(""){_ + _}

  def service = HttpService {
    ///////////////// Misc //////////////////////
    case req @ POST -> Root / "root-element-name" =>
      xml(req)(root => Ok(root.label))

    case req @ GET -> Root / "date" =>
      val date = DateTime(100)
      Ok(date.toRfc1123DateTimeString)
        .withHeaders(Header.Date(date))

    case req @ GET -> Root / "echo-headers" =>
      Ok(req.headers.mkString("\n"))

    ///////////////// Massive Data Loads //////////////////////
    case GET -> Root / "bigstring" =>
      Ok((0 until 1000).map(i => s"This is string number $i").mkString("\n"))

    case req@GET -> Root / "bigstring2" =>
      Ok(Process.range(0, 1000).map(i => s"This is string number $i"))

    case req@GET -> Root / "bigstring3" => Ok(flatBigString)

    case GET -> Root / "zero-chunk" =>
      Ok(Process("", "foo!")).withHeaders(Header.`Transfer-Encoding`(TransferCoding.chunked))

    case GET -> Root / "bigfile" =>
      val size = 40*1024*1024   // 40 MB
      Ok(new Array[Byte](size))

    case req @ POST -> Root / "rawecho" =>
      // The body can be used in the response
      Ok(req.body).withHeaders(Header.`Transfer-Encoding`(TransferCoding.chunked))

    ///////////////// Switch the response based on head of content //////////////////////

    case req@POST -> Root / "challenge1" =>
      val body = req.body.map { c => new String(c.toArray, req.charset.nioCharset)}.toTask

      body.flatMap { s: String =>
        if (!s.startsWith("go")) {
          Ok("Booo!!!")
        } else {
          Ok(emit(s) ++ repeatEval(body))
        }
      }

    case req @ POST -> Root / "challenge2" =>
      val parser = await1[ByteVector] map {
        case bits if (new String(bits.toArray, req.charset.nioCharset)).startsWith("Go") =>
          Task.now(Response(body = emit(bits) ++ req.body))
        case bits if (new String(bits.toArray, req.charset.nioCharset)).startsWith("NoGo") =>
          BadRequest("Booo!")
        case _ =>
          BadRequest("no data")
      }
      (req.body |> parser).eval.toTask

    /*
      case req @ Post -> Root / "trailer" =>
        trailer(t => Ok(t.headers.length))

      case req @ Post -> Root / "body-and-trailer" =>
        for {
          body <- text(req.charset)
          trailer <- trailer
        } yield Ok(s"$body\n${trailer.headers("Hi").value}")
    */

    ///////////////// Weird Route Failures //////////////////////
    case req @ GET -> Root / "hanging-body" =>
      Ok(Process(Task.now(ByteVector(Seq(' '.toByte))), Task.async[ByteVector] { cb => /* hang */}).eval)
        .withHeaders(Header.`Transfer-Encoding`(TransferCoding.chunked))

    case req @ POST -> Root / "ill-advised-echo" =>
      // Reads concurrently from the input.  Don't do this at home.
      implicit val byteVectorMonoidInstance: Monoid[ByteVector] = Monoid.instance(_ ++ _, ByteVector.empty)
      val tasks = (1 to Runtime.getRuntime.availableProcessors).map(_ => req.body.foldMonoid.runLastOr(ByteVector.empty))
      val result = Task.reduceUnordered(tasks)(Reducer.identityReducer)
      Ok(result)

  }
}
