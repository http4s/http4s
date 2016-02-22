/*
 * Forked from https://raw.githubusercontent.com/non/jawn/v0.8.4/support/argonaut/src/main/scala/Parser.scala
 * Licensed under MIT License, Copyright Erik Osheim, 2012-2015.
 * https://github.com/non/jawn#copyright-and-license
 */
package org.http4s.argonaut

import _root_.argonaut._
import _root_.jawn._

import scala.collection.mutable

/* Temporary parser until jawn-argonaut supports 6.2.x. */
private[argonaut] object Parser extends SupportParser[Json] {
  implicit val facade: Facade[Json] =
    new Facade[Json] {
      def jnull() = Json.jNull
      def jfalse() = Json.jFalse
      def jtrue() = Json.jTrue
      def jnum(s: String) = Json.jNumber(JsonNumber.unsafeDecimal(s))
      def jint(s: String) = Json.jNumber(JsonNumber.unsafeDecimal(s))
      def jstring(s: String) = Json.jString(s)

      def singleContext() = new FContext[Json] {
        var value: Json = null
        def add(s: String) { value = jstring(s) }
        def add(v: Json) { value = v }
        def finish: Json = value
        def isObj: Boolean = false
      }

      def arrayContext() = new FContext[Json] {
        val vs = mutable.ListBuffer.empty[Json]
        def add(s: String) { vs += jstring(s) }
        def add(v: Json) { vs += v }
        def finish: Json = Json.jArray(vs.toList)
        def isObj: Boolean = false
      }

      def objectContext() = new FContext[Json] {
        var key: String = null
        var vs = JsonObject.empty
        def add(s: String): Unit =
          if (key == null) { key = s } else { vs = vs + (key, jstring(s)); key = null }
        def add(v: Json): Unit =
        { vs = vs + (key, v); key = null }
        def finish = Json.jObject(vs)
        def isObj = true
      }
    }
}
