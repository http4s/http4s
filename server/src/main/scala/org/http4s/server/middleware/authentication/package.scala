package org.http4s
package server
package middleware

package object authentication {
  // A function mapping (realm, username) to password, None if no password
  // exists for that (realm, username) pair.
  type AuthenticationStore = PartialFunction[(String, String), String]

  case object UserUnknown extends AuthReply

  case object OK extends AuthReply

  case object NoCredentials extends AuthReply

  case object NoAuthorizationHeader extends AuthReply

  case object WrongPassword extends AuthReply

}
