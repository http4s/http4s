package org.http4s

import CharacterSet._
import org.parboiled.common.Base64
import org.http4s.util.CaseInsensitiveString

sealed abstract class Credentials {
  def value: String
  def authScheme: AuthScheme
  override def toString = value
}

case class BasicCredentials(username: String, password: String) extends Credentials {
  val authScheme = AuthScheme.Basic

  lazy val value = {
    val userPass = username + ':' + password
    val bytes = userPass.getBytes(`ISO-8859-1`.charset)
    val cookie = Base64.rfc2045.encodeToString(bytes, false)
    "Basic " + cookie
  }
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

  def value = "Bearer " + token
}


case class GenericCredentials(authScheme: AuthScheme, params: Map[String, String]) extends Credentials {
  lazy val value = if (params.isEmpty) authScheme.toString else formatParams

  private def formatParams = {
    val sb = new java.lang.StringBuilder(authScheme.name).append(' ')
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

