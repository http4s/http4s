package org.http4s

import org.http4s.attributes.{RequestScope, Key}
import scala.language.reflectiveCalls
import concurrent.{Future, ExecutionContext}
import play.api.libs.iteratee._
import akka.util.ByteString

object ExampleRoute extends RouteHandler {
  import Status._
  import Writable._
  import BodyParser._


  val flatBigString = (0 until 1000).map{ i => s"This is string number $i" }.foldLeft(""){_ + _}

  val routeScope = new attributes.AppScope

  object myVar extends Key[Int]

    myVar in routeScope := 0


  def apply(implicit executor: ExecutionContext = ExecutionContext.global): Route = {
    case Get -> Root / "ping" =>
      Done(Ok("pong"))

    case Post -> Root / "echo"  =>
      Done(Ok(Enumeratee.passAlong: Enumeratee[HttpChunk, HttpChunk]))

    case Get -> Root / "echo" =>
      Done(Ok(Enumeratee.map[HttpChunk] {
        case BodyChunk(e) => BodyChunk(e.slice(6, e.length)): HttpChunk
        case chunk => chunk
      }))

    case Get -> Root / "echo2" =>
      Done(Ok(Enumeratee.map[HttpChunk] {
        case BodyChunk(e) => BodyChunk(e.slice(6, e.length)): HttpChunk
        case chunk => chunk
      }))

    case req @ Post -> Root / "sum" =>
      text(req.charset, 16) { s =>
        val sum = s.split('\n').map(_.toInt).sum
        Ok(sum)
      }

    case req @ Get -> Root / "attributes" =>
      req(myVar) = 55
      myVar in routeScope := 1 + (myVar in routeScope value)
      myVar in req := (myVar in req value) + 1
      Ok("Hello" + req(myVar) +  ", and " + (myVar in routeScope value) + ". end.\n")

    case Get -> Root / "html" =>
      Ok(
        <html><body>
          <div id="main">
            <h2>Hello world!</h2><br/>
            <h1>This is H1</h1>
          </div>
        </body></html>
      )

    case req @ Get -> Root / "stream" =>
      Ok(Concurrent.unicast[ByteString]({
        channel =>
          for (i <- 1 to 10) {
            channel.push(ByteString("%d\n".format(i), req.charset.value))
            Thread.sleep(1000)
          }
          channel.eofAndEnd()
      })).addHeader(HttpHeaders.TransferEncoding(HttpEncodings.chunked))

    case Get -> Root / "bigstring" =>
      Done{
        Ok((0 until 1000) map { i => s"This is string number $i" })
      }

    case Get -> Root / "future" =>
      Done{
        Ok(Future("Hello from the future!"))
      }

    case req @ Get -> Root / "bigstring2" =>
      Done{
        Ok(Enumerator((0 until 1000) map { i => ByteString(s"This is string number $i", req.charset.value) }: _*))
      }

    case req @ Get -> Root / "bigstring3" =>
      Done{
        Ok(flatBigString)
      }

    case Get -> Root / "contentChange" =>
      Ok("<h2>This will have an html content type!</h2>", MediaTypes.`text/html`)

      // Ross wins the challenge
    case req @ Get -> Root / "challenge" =>
      Iteratee.head[HttpChunk].map {
        case Some(bits: BodyChunk) if (bits.decodeString(req.charset)).startsWith("Go") =>
          Ok(Enumeratee.heading(Enumerator(bits: HttpChunk)))
        case Some(bits: BodyChunk) if (bits.decodeString(req.charset)).startsWith("NoGo") =>
          BadRequest("Booo!")
        case _ =>
          BadRequest("No data!")
      }

    case req @ Get -> Root / "fail" =>
      sys.error("FAIL")
  }
}