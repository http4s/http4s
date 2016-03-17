package com.example.http4s.oauth2

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future

import scalaoauth2.provider._
import scalaz.\/

import java.util.UUID
import java.util.Date

case class User(name: String)

// Toy implementation.  Leaks memory.  Totally open.  Do not use at work.
class MyDataHandler extends DataHandler[User] {
  private val tokenMap = TrieMap.empty[String, AccessToken]
  private val userMap = TrieMap.empty[(User, String), AccessToken]
  private val authInfoMap = TrieMap.empty[AccessToken, AuthInfo[User]]

  override def validateClient(request: AuthorizationRequest): Future[Boolean] =
    Future.successful {
      request.clientCredential match {
        case Some(_) =>
          true
        case _ =>
          false
      }
    }

  override def getStoredAccessToken(authInfo: AuthInfo[User]): Future[Option[AccessToken]] =
    Future.successful {
      userMap.get((authInfo.user, authInfo.clientId.getOrElse("")))
    }

  override def createAccessToken(authInfo: AuthInfo[User]): Future[AccessToken] =
    authInfo.clientId match {
      case Some(clientId) =>
        val accessToken = AccessToken(UUID.randomUUID.toString, None, None, Some(600L), new Date)
        tokenMap.put(accessToken.token, accessToken)
        userMap.put((authInfo.user, clientId), accessToken)
        authInfoMap.put(accessToken, authInfo)
        Future.successful(accessToken)
      case None =>
        Future.failed(new InvalidClient())
    }

  override def findUser(request: AuthorizationRequest): Future[Option[User]] =
    request match {
      case req @ PasswordRequest(request) if req.password == "p4ssw0rd" =>
        Future.successful(Some(User(req.username)))
      case _ =>
        Future.successful(None)
    }

  override def findAuthInfoByRefreshToken(refreshToken: String): Future[Option[AuthInfo[User]]] =
    Future.successful(None)

  override def refreshAccessToken(authInfo: AuthInfo[User], refreshToken: String): Future[AccessToken] =
    Future.failed(new InvalidClient())

  override def findAuthInfoByCode(code: String): Future[Option[AuthInfo[User]]] =
    Future.successful(None)

  override def deleteAuthCode(code: String): Future[Unit] =
    Future.successful(())

  override def findAccessToken(token: String): Future[Option[AccessToken]] =
    Future.successful(tokenMap.get(token))

  override def findAuthInfoByAccessToken(accessToken: AccessToken): Future[Option[AuthInfo[User]]] =
    Future.successful(authInfoMap.get(accessToken))
}
