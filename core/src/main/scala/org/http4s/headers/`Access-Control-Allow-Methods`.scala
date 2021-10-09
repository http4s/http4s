package org.http4s.headers

import cats.data.NonEmptyList
import org.http4s.Method

/** The `Access-Control-Allow-Methods` header. */
sealed trait `Access-Control-Allow-Methods`

object `Access-Control-Allow-Methods` {

  /** The value "*" only counts as a special wildcard value for requests without credentials
    * (requests without HTTP cookies or HTTP authentication information). In requests with
    * credentials, it is treated as the literal method name "*" without special semantics.
    */
  final case object AllMethods extends `Access-Control-Allow-Methods`

  /** A comma-delimited list of the allowed HTTP request methods.
    * @param methods List of allowed HTTP methods
    */
  final case class Methods(methods: NonEmptyList[Method]) extends `Access-Control-Allow-Methods`
}
