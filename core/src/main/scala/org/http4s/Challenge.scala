package org.http4s

import org.http4s.util.{Writer, Renderable}

case class Challenge(scheme: String, realm: String, params: Map[String, String] = Map.empty) extends Renderable {
  override lazy val value = super.value

  def render[W <: Writer](writer: W) = {
    writer.append(scheme).append(' ')
    writer.append("realm").append("=\"").append(realm).append('"')
    params.foreach{ case (k, v) => addPair(writer, k, v )}
    writer
  }

  @inline
  private def addPair(b: Writer, k: String, v: String) {
    b.append(',').append(k).append("=\"").append(k).append('"')
  }

  override def toString = value
}