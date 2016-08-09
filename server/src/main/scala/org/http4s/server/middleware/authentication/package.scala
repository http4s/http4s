package org.http4s
package server
package middleware

import fs2._

package object authentication {
  // A function mapping (realm, username) to password, None if no password
  // exists for that (realm, username) pair.
  type AuthenticationStore = (String, String) =>  Task[Option[String]]

  val authenticatedUser = AttributeKey.http4s[String]("authenticatedUser")
  val authenticatedRealm = AttributeKey.http4s[String]("authenticatedRealm")
}
