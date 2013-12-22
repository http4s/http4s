package org.http4s
package parser

import org.http4s.Header
import org.http4s.util.CaseInsensitiveString

import scalaz.{Failure, Validation, Success}

/**
 * @author Bryce Anderson
 *         Created on 12/22/13
 */

object HttpParser extends HttpParser

trait HttpParser extends SimpleHeaders {

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
      parserold.HttpParser.rules.map { pair =>
        val ci = pair._1
        val rule1 = pair._2

        def go(input: String): HeaderValidation = {
          Validation.fromEither(parserold.HttpParser.parse(rule1, input))
        }
        (ci, go(_))
      }
    }

  def parseHeader(header: Header): HeaderValidation = {
    header match {
      case x@ Header.RawHeader(name, value) =>
        rules.get(name) match {
          case Some(parser) => parser(value)
          case None => Success(x) // if we don't have a rule for the header we leave it unparsed
        }
      case x => Success(x) // already parsed
    }
  }

  def parseHeaders(headers: List[Header]): (List[String], List[Header]) = {
    val errors = List.newBuilder[String]
    val parsedHeaders = headers.map { header =>
      parseHeader(header) match {
        case Success(parsed) => parsed
        case Failure(error: ParseErrorInfo) => errors += error.detail; header
      }
    }
    (errors.result(), parsedHeaders)
  }
}
