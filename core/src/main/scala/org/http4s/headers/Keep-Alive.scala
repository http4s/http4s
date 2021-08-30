/*
 * Copyright 2013 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s
package headers

import cats.implicits._
import cats.parse._
import org.http4s.internal.parsing.Rfc7230
import org.http4s.util.{Renderable, Writer}
import org.http4s.Header
import org.typelevel.ci._
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import scala.collection.mutable.ListBuffer

object `Keep-Alive` {

sealed trait KeepAlive 
final case class Timeout(timeoutSeconds: Long) extends KeepAlive
final case class Max(max: Long) extends KeepAlive
final case class Extension(ext: (String, Option[String])) extends KeepAlive

/*
https://www.w3.org/Protocols/HTTP/1.1/draft-ietf-http-v11-spec-01#Keep-Alive

Keep-Alive           = "Keep-Alive" ":" 1#keep-alive-info
keep-alive-info      =   "timeout" "=" delta-seconds
                       / "max" "=" 1*DIGIT
                       / keep-alive-extension
keep-alive-extension = token [ "=" ( token / quoted-string ) ]
*/

  def apply(timeoutSeconds: Option[Long], max: Option[Long], extension: List[(String, Option[String])]): ParseResult[`Keep-Alive`] = {
    val validatedTimeoutSeconds = timeoutSeconds.traverse(t => nonNegativeLong(t, "timeout"))
    val validatedMax = max.traverse(m => nonNegativeLong(m, "max"))
    (validatedTimeoutSeconds, validatedMax).mapN((t, m) => new `Keep-Alive`(t, m, extension))
  }

  def unsafeApply(timeoutSeconds: Option[Long], max: Option[Long], extension: List[(String, Option[String])]): `Keep-Alive` = 
    apply(timeoutSeconds, max, extension).fold(throw _, identity)

  def parse(s: String): ParseResult[`Keep-Alive`] = ParseResult.fromParser(parser, "Invalid Keep-Alive header")(s)
  
  private[http4s] def nonNegativeLong(l: Long, fieldName: String): ParseResult[Long] = 
    if(l >= 0) ParseResult.success(l) else ParseResult.fail(s"Invalid long for $fieldName", s"$fieldName which was $l must be greater than or equal to 0 seconds")  

  private[http4s] def safeToLong(s: String): Option[Long] = {
      try { 
        Some(s.toLong)
      } catch { 
        case _: NumberFormatException => None
      }
    }
  private[http4s] val parser: Parser[`Keep-Alive`] = {
    import Rfc7230.{quotedString, token, headerRep1}
    import Numbers.digits

  //"timeout" "=" delta-seconds
  val timeout: Parser[Timeout] = Parser.string("timeout=") *> digits.mapFilter(s => safeToLong(s).map(Timeout))

  //"max" "=" 1*DIGIT
  val max: Parser[Max] = Parser.string("max=") *> digits.mapFilter(s => safeToLong(s).map(Max))

  //keep-alive-extension = token [ "=" ( token / quoted-string ) ]
  val keepAliveExtension: Parser[Extension] = (token ~ (Parser.char('=') *> (token).orElse(quotedString).?)).map(p => Extension(p))
  
  /*
  keep-alive-info      = "timeout" "=" delta-seconds
                       / "max" "=" 1*DIGIT
                       / keep-alive-extension
  */
  val keepAliveInfo: Parser[KeepAlive] = timeout.orElse(max).orElse(keepAliveExtension)
  
  headerRep1(keepAliveInfo).map { nel => 
    var timeoutSeconds: Option[Long] = None
    var max: Option[Long] = None
    val extension: ListBuffer[(String, Option[String])] = ListBuffer.empty
    nel.foldLeft(()) { (_, ka) => 
      ka match { 
        case Timeout(n) => if(timeoutSeconds.isEmpty) timeoutSeconds = Some(n) else ()
        case Max(n) => if(max.isEmpty) max = Some(n) else ()
        case Extension(p) => extension.append(p)
      }
    }
    unsafeApply(timeoutSeconds, max, extension.toList)
  }
}
//Arb keep alive for testing.  IN tests module.  
// Arb option of non neg long and arb list of token -> any string

//For the below:  No hanging ',' so we could make it all one list and use the nonempty list writer?
implicit val headerInstance: Header[`Keep-Alive`, Header.Recurring] = Header.createRendered(ci"Keep-Alive", 
       v => new Renderable {
          def render(writer: Writer): writer.type =
            v match {
              case `Keep-Alive`(t,m,e) =>
                t.foreach(l => writer << "timeout=" << l << ", ")
                m.foreach(l => writer << "max=" << l << ", ")
                e.foreach { 
                  p => writer << p._1 << "="
                  p._2.foreach(s => writer << s)
                  writer << ", "
                }
               writer
            }
          }, 
          parse)


}

final case class `Keep-Alive`private (timeoutSeconds: Option[Long], max: Option[Long], extension: List[(String, Option[String])]) { 
  def toTimeoutDuration: Option[FiniteDuration] = timeoutSeconds.map(FiniteDuration(_, TimeUnit.SECONDS))
}
