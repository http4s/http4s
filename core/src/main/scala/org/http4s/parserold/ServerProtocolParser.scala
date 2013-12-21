package org.http4s
package parserold

import BasicRules._
import org.http4s.{ServerProtocol => SP}
import scalaz.Validation

private[http4s] object ServerProtocolParser extends Http4sParser {
  def apply(s: String): Validation[ParseErrorInfo, ServerProtocol] = Validation.fromEither(parse(ServerProtocol, s))

  def ServerProtocol = rule { HttpVersion | Included | ExtensionVersion }

  def HttpVersion = rule { ignoreCase("HTTP") ~ "/" ~ Version ~~> {
    version => SP.HttpVersion(version.major, version.minor)
  }}

  def Included = rule { ignoreCase("INCLUDED") ~> (_ => SP.INCLUDED) }

  def ExtensionVersion = rule { Token ~ optional("/" ~ Version) ~~> {
    (protocol, version) => SP.ExtensionVersion(protocol.ci, version)
  }}

  def Version = rule { Number ~ "." ~ Number ~~> {(major, minor) => SP.Version(major, minor)} }

  def Number = rule { oneOrMore(Digit) ~> (_.toInt) }
}
