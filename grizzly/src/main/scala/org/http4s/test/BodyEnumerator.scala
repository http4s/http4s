package org.http4s
package test

import play.api.libs.iteratee._
import concurrent.{ExecutionContext, Promise, Future}
import org.glassfish.grizzly.http.server.io.NIOInputStream
import org.glassfish.grizzly.ReadHandler

/**
 * @author Bryce Anderson
 *
 */
class BodyEnumerator(is: NIOInputStream, chunkSize:Int = 32 * 1024)(implicit ctx: ExecutionContext) extends Enumerator[Chunk] {
  def apply[A](i: Iteratee[org.http4s.Chunk, A]): Future[Iteratee[org.http4s.Chunk, A]] = {

    i.fold {
      case Step.Cont(f) => {
        val promise = Promise[Iteratee[_root_.org.http4s.Chunk, A]]
        is.notifyAvailable(new ReadHandler {
          var currentContinuation = f

          def onError(t: Throwable) {
            sys.error(s"Error in ReadHandler of custom iterattor: $t")
          }

          def onAllDataRead() {
            //println("Got here: onAllDataRead")
            val bytes = new Chunk(is.readyData())
            val readBytes = is.read(bytes,0,bytes.length)
            val newItter = f(Input.El(bytes.take(readBytes)))

            promise.completeWith(Enumerator.eof |>> newItter)
          }

          def onDataAvailable() {
            //println("Got here: onDataAvailable")
            val bytes = new Chunk(is.readyData())
            val readBytes = is.read(bytes,0,bytes.length)
            val newItter = f(Input.El(bytes.take(readBytes)))

            newItter.pureFold{
                case Step.Cont(f) =>
                  currentContinuation = f
                  is.notifyAvailable(this)

                case Step.Done(a,e) => promise.completeWith(Future(Done(a,e)))
                case Step.Error(msg,e) => sys.error(s"Iteratee returned an error: $e")
            }
          }
        }, chunkSize)
        promise.future
      } // case Step.Cont

        // TODO: Should probably deal with the remaining input, in case an interatee just got too much...
      case Step.Done(result,remaining) => Future(Done(result,remaining))
      case Step.Error(msg, remaining) => sys.error(msg)
    }
  }


}
