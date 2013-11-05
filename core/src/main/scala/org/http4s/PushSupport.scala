package org.http4s

import scala.concurrent.Future
import play.api.libs.iteratee.Iteratee
import org.http4s

/**
 * @author Bryce Anderson
 *         Created on 11/5/13
 */
trait PushSupport { self: Responder =>

  import scala.concurrent.ExecutionContext.Implicits.global

  type PushResponder = Responder with PushSupport

  private def pushCascade(url: String, content: Future[Responder]): Future[Vector[(String, Responder)]] = {
    content.flatMap { r =>
      r.attributes.get(PushSupport.pushKey)
        .map(fl => fl.map(f => f:+(url, r)))
        .getOrElse(Future.successful(Vector((url, r))))
    }
  }
  
  def push(url: String, content: Future[Responder], cascade: Boolean): Responder = {
    val newPushContent: Future[Vector[(String, Responder)]] = {
      if (cascade) pushCascade(url, content)
      else content.map( resp => Vector((url, resp)))
    }

    val newFullList: Future[Vector[(String,Responder)]] = self.attributes.get(PushSupport.pushKey)
      .map(fl => fl.flatMap(l => newPushContent.map(l ++ _)))
      .getOrElse(newPushContent)

    new http4s.Responder(self.prelude, self.body,
      self.attributes.put(PushSupport.pushKey, newFullList)) with PushSupport
  }

  def push(url: String, content: Iteratee[Chunk, Responder], cascade: Boolean): Responder =
    push(url, content.run, cascade)

  def push(url: String, content: Responder, cascade: Boolean): Responder =
    push(url, Future.successful(content), cascade)

}


object PushSupport {
  private[http4s] val pushKey = AttributeKey[Future[Vector[(String, Responder)]]]("http4sPush")
}