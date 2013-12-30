package org.http4s

import org.http4s.util.Renderable

case class Challenge(scheme: String, realm: String, params: Map[String, String] = Map.empty) extends Renderable {
  override lazy val value = super.value

  def render(builder: StringBuilder): StringBuilder = {
    builder.append(scheme).append(' ')
    builder.append("realm").append("=\"").append(realm).append('"')
    params.foreach{ case (k, v) => addPair(builder, k, v )}
    builder
  }

  @inline
  private def addPair(b: StringBuilder, k: String, v: String) {
    b.append(',').append(k).append("=\"").append(k).append('"')
  }

  override def toString = value
}