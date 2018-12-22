/*
 * Forked from https://raw.githubusercontent.com/non/jawn/v0.8.4/support/argonaut/src/main/scala/Parser.scala
 * Licensed under MIT License, Copyright Erik Osheim, 2012-2015.
 * https://github.com/non/jawn#copyright-and-license
 */
package org.http4s.argonaut

import _root_.argonaut._
import org.typelevel.jawn._
import scala.collection.mutable

/* Temporary parser until jawn-argonaut supports 6.2.x. */
private[argonaut] object Parser extends SupportParser[Json] {
  implicit val facade: Facade[Json] =
    new Facade[Json] {
      override def jnull() = Json.jNull
      override def jfalse() = Json.jFalse
      override def jtrue() = Json.jTrue
      override def jnum(s: CharSequence, decIndex: Int, expIndex: Int): Json =
        Json.jNumber(JsonNumber.unsafeDecimal(s.toString))
      override def jstring(s: CharSequence): Json = Json.jString(s.toString)

      def singleContext() = new FContext[Json] {
        var value: Json = null
        def add(s: CharSequence): Unit = { value = jstring(s); () }
        def add(v: Json): Unit = { value = v; () }
        def finish: Json = value
        def isObj: Boolean = false
      }

      def arrayContext() = new FContext[Json] {
        val vs = mutable.ListBuffer.empty[Json]
        def add(s: CharSequence): Unit = { vs += jstring(s); () }
        def add(v: Json): Unit = { vs += v; () }
        def finish: Json = Json.jArray(vs.toList)
        def isObj: Boolean = false
      }

      def objectContext() = new FContext[Json] {
        var key: String = null
        var vs = JsonObject.empty
        def add(s: CharSequence): Unit =
          if (key == null) { key = s.toString } else { vs = vs + (key, jstring(s)); key = null }
        def add(v: Json): Unit = { vs = vs + (key, v); key = null }
        def finish = Json.jObject(vs)
        def isObj = true
      }
    }
}
