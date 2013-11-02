package org.http4s

case class Challenge(scheme: String, realm: String, params: Map[String, String] = Map.empty) {
  def value = scheme + ' ' + (("realm" -> realm) :: params.toList).map {
    case (k, v) => k + "=\"" + v + '"'
  }.mkString(",")

  override def toString = value
}