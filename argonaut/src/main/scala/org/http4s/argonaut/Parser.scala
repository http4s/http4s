/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Based on https://github.com/typelevel/jawn/blob/v0.8.4/parser/src/main/scala/jawn/Parser.scala
 * Copyright Erik Osheim, 2012-2015
 * See licenses/LICENSE_jawn
 */

package org.http4s.argonaut

import _root_.argonaut._
import org.typelevel.jawn._
import scala.collection.mutable

/* Temporary parser until jawn-argonaut supports 6.2.x. */
private[argonaut] object Parser extends SupportParser[Json] {
  implicit val facade: Facade[Json] =
    new Facade.NoIndexFacade[Json] {
      override def jnull: Json = Json.jNull
      override def jfalse: Json = Json.jFalse
      override def jtrue: Json = Json.jTrue
      override def jnum(s: CharSequence, decIndex: Int, expIndex: Int): Json =
        Json.jNumber(JsonNumber.unsafeDecimal(s.toString))
      override def jstring(s: CharSequence): Json = Json.jString(s.toString)

      def singleContext(): FContext[Json] =
        new FContext.NoIndexFContext[Json] {
          var value: Json = null
          def add(s: CharSequence): Unit = { value = jstring(s); () }
          def add(v: Json): Unit = { value = v; () }
          def finish(): Json = value
          def isObj: Boolean = false
        }

      def arrayContext(): FContext[Json] =
        new FContext.NoIndexFContext[Json] {
          val vs = mutable.ListBuffer.empty[Json]
          def add(s: CharSequence): Unit = { vs += jstring(s); () }
          def add(v: Json): Unit = { vs += v; () }
          def finish(): Json = Json.jArray(vs.toList)
          def isObj: Boolean = false
        }

      def objectContext(): FContext[Json] =
        new FContext.NoIndexFContext[Json] {
          var key: String = null
          var vs = JsonObject.empty
          def add(s: CharSequence): Unit =
            if (key == null)
              key = s.toString
            else {
              vs = vs.+(key, jstring(s))
              key = null
            }
          def add(v: Json): Unit = {
            vs = vs.+(key, v)
            key = null
          }
          def finish() = Json.jObject(vs)
          def isObj = true
        }
    }
}
