package org.http4s

import CharacterSet._
import org.parboiled.common.Base64
import org.http4s.util.{Renderable, CaseInsensitiveString}

sealed abstract class Credentials extends Renderable {
  def authScheme: AuthScheme
  override def toString = value
}

case class BasicCredentials(username: String, password: String) extends Credentials {
  val authScheme = AuthScheme.Basic

  override lazy val value = {
    val userPass = username + ':' + password
    val bytes = userPass.getBytes(`ISO-8859-1`.charset)
    val cookie = Base64.rfc2045.encodeToString(bytes, false)
    "Basic " + cookie
  }

  def render(builder: StringBuilder): StringBuilder = builder.append(value)
}

object BasicCredentials {
  def apply(credentials: String): BasicCredentials = {
    val bytes = Base64.rfc2045.decodeFast(credentials)
    val userPass = new String(bytes, `ISO-8859-1`.charset)
    userPass.indexOf(':') match {
      case -1 => apply(userPass, "")
      case ix => apply(userPass.substring(0, ix), userPass.substring(ix + 1))
    }
  }
}


case class OAuth2BearerToken(token: String) extends Credentials {
  val authScheme = AuthScheme.Bearer

  def render(builder: StringBuilder): StringBuilder = builder.append("Bearer ").append(token)
}


case class GenericCredentials(authScheme: AuthScheme, params: Map[String, String]) extends Credentials {
  override lazy val value = super.value

  def render(builder: StringBuilder): StringBuilder = {
    if (params.isEmpty) builder.append(authScheme.toString)
    else {
      formatParams(builder)
      builder
    }
  }

  private def formatParams(sb: StringBuilder) = {
    var first = true
    params.foreach {
      case (k, v) =>
        if (first) first = false else sb.append(',')
        if (k.isEmpty) sb.append('"') else sb.append(k).append('=').append('"')
        v.foreach {
          case '"' => sb.append('\\').append('"')
          case '\\' => sb.append('\\').append('\\')
          case c => sb.append(c)
        }
        sb.append('"')
    }
    sb.toString
  }
}

