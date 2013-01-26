package org.http4s

case class ContentType(mediaType: MediaType, params: List[(String, String)] = Nil)

case class MediaType(mainType: String, subtype: String, params: List[(String, String)])
