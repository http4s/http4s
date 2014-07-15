package org.http4s.server.websocket

sealed trait WSFrame
case class Text(msg: String) extends WSFrame
case class Binary(msg: Array[Byte]) extends WSFrame
