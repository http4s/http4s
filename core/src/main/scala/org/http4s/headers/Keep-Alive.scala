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

object `Keep-Alive` {
sealed trait KeepAlive 
final case class Timeout(timeout: Duration) extends KeepAlive
final case class Max(max: Long) extends KeepAlive
final case class Extension(ext: Map[String, String]) extends KeepAlive

/*
Keep-Alive           = "Keep-Alive" ":" 1#keep-alive-info
keep-alive-info      =   "timeout" "=" delta-seconds
                       / "max" "=" 1*DIGIT
                       / keep-alive-extension
keep-alive-extension = token [ "=" ( token / quoted-string ) ]
*/

  def apply(timeout: Duration, max: Int, extension: Map[String, String]): `Keep-Alive` = `Keep-Alive`(timeout, max, extension)

  def parse(s: String): ParseResult[`Keep-Alive`] = ParseResult.fromParser(parser, "Invalid Keep-Alive header")(s)
  
  private[http4s] val parser: Parser[`Keep-Alive`] = {
    import Rfc5234.digit

  //"timeout" "=" delta-seconds
  val timeout: Parser[Timeout] = Parser.string("timeout=") *> digit.rep.string.map(s => Timeout(Duration.ofSeconds(s.toLong)))

  //"max" "=" 1*DIGIT
  val max: Parser[Max] = Parser.string("max=") *> digit.rep.string.map(s => Max(s.toLong))

  //keep-alive-extension = token [ "=" ( token / quoted-string ) ]
  val ext: Parser[Extension] = Parser.anyChar.rep *> Parser.char('=') *> (Parser.anyChar.rep).orElse(Parser.anyChar.rep.surroundedBy(Parser.char('=')))
  timeout.orElse(max).orElse(ext)
 }

}

final case class `Keep-Alive`(timeout: Duration, max: Long, extension: Map[String, String])