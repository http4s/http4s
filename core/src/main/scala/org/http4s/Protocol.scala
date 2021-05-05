package org.http4s

import org.typelevel.ci.CIString

final case class Protocol(name: CIString, version: Option[CIString]) {
  override def toString(): String = name.toString + version.map(_.toString).getOrElse("")
}
