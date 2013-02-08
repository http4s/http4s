package org.http4s
package test

import play.api.libs.iteratee._
import concurrent.{ExecutionContext, Promise, Future}
import org.glassfish.grizzly.http.server.io.NIOInputStream
import org.glassfish.grizzly.ReadHandler

/**
 * Created with IntelliJ IDEA.
 * User: brycea
 * Date: 2/7/13
 * Time: 8:31 PM
 * To change this template use File | Settings | File Templates.
 */
class CustomEnumerator(is: NIOInputStream)(implicit ctx: ExecutionContext) extends Enumerator[Chunk] {
  var leftOvers: Option[Chunk] = None
  var running = false

  def apply[A](i: Iteratee[_root_.org.http4s.Chunk, A]): Future[Iteratee[_root_.org.http4s.Chunk, A]] = {

    val promise = Promise[Iteratee[_root_.org.http4s.Chunk, A]]

    // Deal with the leftovers if there are any
    // Could probably do better than leaving a variable dangling around
    /*
    The best course of action would be to see if we are actually going to continue at all...
     */
    var iFuture: Future[Iteratee[Chunk, A]] = leftOvers.fold {Future.successful(i)} { leftOver =>
      println("Found leftovers!")
      i.pureFold{
        case Step.Cont(f) =>
          val bytes = new Chunk(is.readyData())
          val readBytes = is.read(bytes,0,bytes.length)
          val result = f(Input.El(bytes.take(readBytes)))
          result

        case Step.Done(a,e) =>
          e match {
            case Input.El(e) => leftOvers = Some(e)
            case _ => sys.error("Got strange type of data back while doing leftovers!")
          }
          val result = Done(a,e)
          promise.completeWith(Future(result))
          result

        case Step.Error(msg,e) => sys.error(s"Iteratee returned an error during leftovers: $e")
      }
      Future.successful(i)
    }

    is.notifyAvailable(new ReadHandler {
      def onError(t: Throwable) {
        sys.error(s"Error in ReadHandler: ${t.toString}")
      }

      def onDataAvailable() {
        println("Got here: onDataAvailable")
        val bytes = new Chunk(is.readyData())
        val readBytes = is.read(bytes,0,bytes.length)
        iFuture = iFuture.flatMap{ it =>
          it.pureFold{
            case Step.Cont(f) =>

              val result = f(Input.El(bytes.take(readBytes)))
              is.notifyAvailable(this)
              result

            case Step.Done(a,e) =>
              println("Got to done")
              leftOvers = Some(bytes)
              val result = Done(a,e)
              promise.completeWith(Future(result))
              result


            case Step.Error(msg,e) => sys.error(s"Iteratee returned an error: $e")
          }
        }
      }

      def onAllDataRead() {  // End the Iteratee
        println("Got here: onAllDataRead!")
        promise.completeWith(iFuture.flatMap( i => i.feed(Input.EOF)))

      }
    })

    promise.future
  }
}
