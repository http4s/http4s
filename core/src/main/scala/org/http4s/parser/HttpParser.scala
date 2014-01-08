package org.http4s
package parser

import org.http4s.Header
import org.http4s.util.CaseInsensitiveString

import scalaz.{Failure, Validation, Success}
import org.http4s.parserold.QueryParser

/**
 * @author Bryce Anderson
 *         Created on 12/22/13
 */

object HttpParser extends HttpParser

trait HttpParser extends SimpleHeaders
                    with AcceptHeader
                    with AcceptLanguageHeader
                    with ContentTypeHeader
                    with AcceptRangesHeader
                    with AcceptCharsetHeader
                    with AcceptEncodingHeader {

  type HeaderValidation = Validation[ParseErrorInfo, Header]

  type HeaderParser = String => HeaderValidation

  val rules: Map[CaseInsensitiveString, HeaderParser] =
    this
      .getClass
      .getMethods
      .filter(_.getName.forall(!_.isLower)) // only the header rules have no lower-case letter in their name
      .map { method =>
        method.getName.replace('_', '-').ci -> { value: String =>
          method.invoke(this, value)
        }.asInstanceOf[HeaderParser]
      }.toMap ++ {      // TODO: remove all the older parsers and replace with parboiled2 parsers!
      parserold.HttpParser.oldrules.map { pair =>
        val ci = pair._1
        val rule1 = pair._2

        def go(input: String): HeaderValidation = {
          Validation.fromEither(parserold.HttpParser.parse(rule1, input))
        }
        (ci, go(_))
      }
    }

  def parseHeader(header: Header.RawHeader): HeaderValidation = {
    rules.get(header.name) match {
      case Some(parser) => parser(header.value)
      case None => Success(header) // if we don't have a rule for the header we leave it unparsed
    }
  }

  def parseHeaders(headers: List[Header]): (List[String], List[Header]) = {
    val errors = List.newBuilder[String]
    val parsedHeaders = headers.map {   // Only attempt to parse the raw headers
      case header: Header.RawHeader =>
        parseHeader(header) match {
          case Success(parsed) => parsed
          case Failure(error: ParseErrorInfo) => errors += error.detail; header
        }

      case header => header
    }
    (errors.result(), parsedHeaders)
  }

  /**
   * Warms up the spray.http module by triggering the loading of most classes in this package,
   * so as to increase the speed of the first usage.
   */
  def warmUp() {
    QueryParser.parseQueryString("a=b&c=d")

    val results = HttpParser.parseHeaders(List(
      Header("Accept", "*/*,text/plain,custom/custom"),
      Header("Accept-Charset", "*,UTF-8"),
      Header("Accept-Encoding", "gzip,custom"),
      Header("Accept-Language", "*,nl-be,custom"),
      Header("Authorization", "Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ=="),
      Header("Cache-Control", "no-cache"),
      Header("Connection", "close"),
      Header("Content-Disposition", "form-data"),
      Header("Content-Encoding", "deflate"),
      Header("Content-Length", "42"),
      Header("Content-Type", "application/json"),
      Header("Cookie", "http4s=cool"),
      Header("Host", "http4s.org"),
      Header("X-Forwarded-For", "1.2.3.4"),
      Header("Fancy-Custom-Header", "yeah")
    ))

    assert(results._1.isEmpty)
  }
}
