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

//import cats.data.NonEmptyList
import cats.parse._
import com.comcast.ip4s.IpAddress
import org.http4s.internal.parsing.{Rfc3986, Rfc7230}
import org.http4s.util.{Renderable, Writer}
import org.http4s.Header
import org.typelevel.ci._
import java.time.Duration
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit

object `Keep-Alive` {
sealed trait KeepAlive 
final case class Timeout(timeoutSeconds: Long) extends KeepAlive
final case class Max(max: Long) extends KeepAlive
final case class Extension(ext: (String, Option[String])) extends KeepAlive

/*
Keep-Alive           = "Keep-Alive" ":" 1#keep-alive-info
keep-alive-info      =   "timeout" "=" delta-seconds
                       / "max" "=" 1*DIGIT
                       / keep-alive-extension
keep-alive-extension = token [ "=" ( token / quoted-string ) ]
*/
//Keep alive portion is handled by Header.rep1 
//Unsafe constructor and rigid constructor please. 
  def apply(timeoutSeconds: Long, max: Long, extension: Map[String, Option[String]]): `Keep-Alive` = `Keep-Alive`(timeoutSeconds, max, extension)
  def safeApply(timeoutSeconds: Long, max: Long, extension: Map[String, Option[String]]): Option[`Keep-Alive`] = ???

  def parse(s: String): ParseResult[`Keep-Alive`] = ParseResult.fromParser(parser, "Invalid Keep-Alive header")(s)
  
  val parser: Parser[`Keep-Alive`] = { //private[http4s]  Make this private after double checking
    import Rfc7230.{quotedString, token, headerRep1}
    import Numbers.digits

    def safeToLong(s: String): Option[Long] =
      try { 
        Some(s.toLong)
      } catch { 
        case _: NumberFormatException => None
      }

  //TODO:  Parser for those strings will need "" added to them as literals
  //"timeout" "=" delta-seconds
  val timeout: Parser[Timeout] = Parser.string("timeout=") *> digits.mapFilter(s => safeToLong(s).map(Timeout))

  //"max" "=" 1*DIGIT
  val max: Parser[Max] = Parser.string("max=") *> digits.mapFilter(s => safeToLong(s).map(Max))

  //keep-alive-extension = token [ "=" ( token / quoted-string ) ]
  val ext: Parser[Extension] = (token ~ (Parser.char('=') *> (token).orElse(quotedString).?)).map(p => Extension(p))
  val oneOfThese: Parser[KeepAlive] = timeout.orElse(max).orElse(ext)
  
  //State of the fold
  case class KO(t: Option[Long], m: Option[Long], ex: Map[String, Option[String]])
  headerRep1(oneOfThese).map { nel => 
    nel.foldLeft(KO(None, None, Map.empty)) { (acc, ka) => 
      ka match { 
        case Timeout(n) => if(acc.t.isEmpty) acc.copy(t = Some(n)) else acc //Do we want to throw away the other timeouts?
        case Max(n) => if(acc.m.isEmpty) acc.copy(m = Some(n)) else acc //Same as above
        case Extension(p) => acc.copy(ex = )
      }
    }  
  }
   
}
/*
  implicit val headerInstance: Header[`Keep-Alive`, Header.Single] = //Check on single or recurring. 
    Header.createRendered(
      ci"Keep-Alive",
      A => B,
      parse
    )
*/
}

final case class `Keep-Alive`(timeoutSeconds: Long, max: Long, extension: Map[String, Option[String]]) { 
  def toTimeoutDuration: FiniteDuration = FiniteDuration(timeoutSeconds, TimeUnit.SECONDS)
}