package org.http4s
package headers

import org.http4s.Header.Raw
import org.http4s.parser.Http4sHeaderParser
import org.http4s.util.Writer
import org.http4s.util.string._
import org.parboiled2._

object Authorization extends HeaderKey.Internal[Authorization] with HeaderKey.Singleton  {
  override protected def parseHeader(raw: Raw): Option[Authorization] =
    new AuthorizationParser(raw.value).parse.toOption

  private class AuthorizationParser(input: ParserInput) extends Http4sHeaderParser[Authorization](input) {
    def entry: Rule1[Authorization] = rule {
      CredentialDef ~ EOI ~> (Authorization(_))
    }

    def CredentialDef = rule {
      BasicCredentialDef | OAuth2BearerTokenDef | GenericHttpCredentialsDef
    }

    def BasicCredentialDef: Rule1[BasicCredentials] = rule {
      "Basic" ~ oneOrMore(LWS) ~ capture(BasicCookie) ~> {s: String => BasicCredentials(s) }
    }

    def BasicCookie: Rule0 = rule {
      oneOrMore(Base64Char) ~ optional("==" | ch('='))
    }

    def OAuth2BearerTokenDef: Rule1[OAuth2BearerToken] = rule {
      "Bearer" ~ oneOrMore(LWS) ~ b64token ~> (OAuth2BearerToken(_))
    }

    def GenericHttpCredentialsDef = rule {
      Token ~ OptWS ~ CredentialParams ~> { (scheme: String, params: Map[String, String]) =>
        GenericCredentials(scheme.ci, params) }
    }

    def CredentialParams: Rule1[Map[String, String]] = rule {
      oneOrMore(AuthParam).separatedBy(ListSep) ~> (_.toMap) |
        (Token | QuotedString) ~> (param => Map("" -> param)) |
        push(Map.empty[String, String])
    }

    def AuthParam: Rule1[(String, String)] = rule {
      Token ~ "=" ~ (Token | QuotedString) ~> { (s1: String, s2: String) => (s1, s2) }
    }

    def Base64Char: Rule0 = rule { Alpha | Digit | '+' | '/' | '=' }

    // https://tools.ietf.org/html/rfc6750#page-5
    def b64token: Rule1[String] = rule {
      capture(oneOrMore(Alpha | Digit | anyOf("-._~+/")) ~ zeroOrMore('=') )
    }
  }
}

final case class Authorization(credentials: Credentials) extends Header.Parsed {
  override def key = `Authorization`
  override def renderValue(writer: Writer): writer.type = credentials.render(writer)
}
