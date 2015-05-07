package org.http4s
package server
package middleware
package authentication

import java.security.MessageDigest

object DigestUtil {
  private def bytes2hex(bytes: Array[Byte]) = bytes.map("%02x".format(_)).mkString

  private def md5(str: String) = bytes2hex(MessageDigest.getInstance("MD5").digest(str.getBytes))

  /**
   * Computes the response value used in Digest Authentication.
   * @param method
   * @param username
   * @param realm
   * @param password
   * @param uri
   * @param nonce
   * @param nc
   * @param cnonce
   * @param qop
   * @return
   */
  def computeResponse(method: String, username: String, realm: String, password: String, uri: String, nonce: String, nc: String, cnonce: String, qop: String) = {
    val ha1str = username + ":" + realm + ":" + password
    val ha1 = md5(ha1str)
    val ha2str = method + ":" + uri
    val ha2 = md5(ha2str)
    val respstr = ha1 + ":" + nonce + ":" + nc + ":" + cnonce + ":" + qop + ":" + ha2
    md5(respstr)
  }
}
