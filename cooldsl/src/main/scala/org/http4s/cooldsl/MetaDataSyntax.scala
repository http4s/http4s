package org.http4s.cooldsl

/**
 * Created by Bryce Anderson on 5/9/14.
 */
trait MetaDataSyntax {
  type Self

  protected def addMetaData(data: MetaData): Self

  final def ^(desc: String): Self = description(desc)

  final def description(desc: String) = addMetaData(PathDescription(desc))
}
