package org.http4s

case class AuthedRequest[A](authInfo: A, req: Request)
