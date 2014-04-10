package org.http4s

import org.http4s.util.{Writer, ValueRenderable}

case class Challenge(scheme: String,
                     realm: String,
                     params: Map[String, String] = Map.empty) extends ValueRenderable {
  override lazy val value = super.value

  def renderValue[W <: Writer](writer: W) = {
    writer.append(scheme).append(' ')
    writer.append("realm=\"").append(realm).append('"')
    params.foreach{ case (k, v) => addPair(writer, k, v )}
    writer
  }

  @inline
  private def addPair(b: Writer, k: String, v: String) {
    b.append(',').append(k).append("=\"").append(v).append('"')
  }

  override def toString = value
}