package org.http4s
package parser

import scalaz.Validation
import org.http4s.util.string._

import org.parboiled2._

private[http4s] object ServerProtocolParser {
  def apply(s: String): Validation[ParseErrorInfo, ServerProtocol] = {
    new ServerProtocolImpl(s).ParseServerProtocol.run()(validationScheme)
  }

  private class ServerProtocolImpl(val input: ParserInput) extends Parser with Rfc2616BasicRules {

    def ParseServerProtocol = rule { HttpVersion | Included | ExtensionVersion }

    def HttpVersion: Rule1[ServerProtocol.HttpVersion] = rule {
      ignoreCase("http") ~ "/" ~ Version ~> { version: ServerProtocol.Version =>
        ServerProtocol.HttpVersion(version.major, version.minor)
    }}

    def Included = rule { ignoreCase("INCLUDED") ~ push(ServerProtocol.INCLUDED) }

    def ExtensionVersion: Rule1[ServerProtocol.ExtensionVersion] = rule {
      Token ~ optional("/" ~ Version) ~> { (protocol: String, version: Option[ServerProtocol.Version]) =>
        ServerProtocol.ExtensionVersion(protocol.ci, version)
      }
    }

    def Version: Rule1[ServerProtocol.Version] = rule {
      Number ~ "." ~ Number ~> {(major: Int, minor: Int) => ServerProtocol.Version(major, minor) }
    }

    def Number: Rule1[Int] = rule { capture(oneOrMore(Digit)) ~> { i: String => i.toInt } }

  }


}
