/*
 * Copyright 2019 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s.ember.server

import scodec.bits.ByteVector

/** TEST-ONLY certificates and private keys, valid from 2026 to 2126. Never use them outside tests.
  *
  * The server and client certificates are issued by the CA. The untrusted
  * client certificate is self-signed and is not trusted by anything.
  *
  * They were generated with OpenSSL 3.5 as P-256 keys, roughly:
  * {{{
  * openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out <name>.key
  * openssl x509 -req -in <name>.csr -CA ca.pem -CAkey ca.key -sha256 \
  *   -not_before 20260101000000Z -not_after 21260101000000Z -extfile ext.cnf -extensions <name>
  * }}}
  * The CA has `CA:TRUE`. All other certificates have SAN `DNS:localhost,IP:127.0.0.1`; the
  * server's has `serverAuth` and the clients' have `clientAuth`. The client certificates need
  * the SAN too, because s2n (Scala Native) checks them against the server name the client sent.
  */
private[server] object TestCertificates {

  val caPem: String =
    """-----BEGIN CERTIFICATE-----
      |MIIBZjCCAQugAwIBAgIBATAKBggqhkjOPQQDAjAZMRcwFQYDVQQDDA5odHRwNHMg
      |dGVzdCBDQTAgFw0yNjAxMDEwMDAwMDBaGA8yMTI2MDEwMTAwMDAwMFowGTEXMBUG
      |A1UEAwwOaHR0cDRzIHRlc3QgQ0EwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQ7
      |VZxcjWgLgwKhra8EB4cYWqyXeKJLr1IrqC/mR2X/4zzbidu982OxW3X/EbwvpxrG
      |W4iPodyazpifFm0MXqfmo0IwQDAPBgNVHRMBAf8EBTADAQH/MA4GA1UdDwEB/wQE
      |AwIBBjAdBgNVHQ4EFgQUwr1ysIqKwRGUHsekBZxLQZ9gtWQwCgYIKoZIzj0EAwID
      |SQAwRgIhAP/rlY3PgX/9Yhk8Xr6Mz3LfgQB5++twQQk5pZPya7+oAiEA+T2QD9Sj
      |S77OW8bmC+s4ut3jiNPHQw5qCfL7jw8WaW4=
      |-----END CERTIFICATE-----
      |""".stripMargin

  val serverPem: String =
    """-----BEGIN CERTIFICATE-----
      |MIIBujCCAWCgAwIBAgIBAjAKBggqhkjOPQQDAjAZMRcwFQYDVQQDDA5odHRwNHMg
      |dGVzdCBDQTAgFw0yNjAxMDEwMDAwMDBaGA8yMTI2MDEwMTAwMDAwMFowHTEbMBkG
      |A1UEAwwSaHR0cDRzIHRlc3Qgc2VydmVyMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcD
      |QgAEAfXknDWILoQiWXxEylbLxf8XamMHwxs57nFSeJL3Vc9swyLuX6SOtykRvuWO
      |q3+hj2x50eXhzPKuruw7+0CkdKOBkjCBjzAMBgNVHRMBAf8EAjAAMA4GA1UdDwEB
      |/wQEAwIHgDATBgNVHSUEDDAKBggrBgEFBQcDATAaBgNVHREEEzARgglsb2NhbGhv
      |c3SHBH8AAAEwHQYDVR0OBBYEFIjbCskEY2VSehjOQ5+CzRaebidZMB8GA1UdIwQY
      |MBaAFMK9crCKisERlB7HpAWcS0GfYLVkMAoGCCqGSM49BAMCA0gAMEUCICzGN80P
      |4i0u3f+iPmkoahP94EBgZSfaKFkeVezjx3MXAiEA6agRNwnCtvAi1GzaTsDtFES9
      |7Ch1gSADnrBh5NXRpWk=
      |-----END CERTIFICATE-----
      |""".stripMargin

  val serverKeyPem: String =
    """-----BEGIN PRIVATE KEY-----
      |MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgHHRc30qhpp0aFiIw
      |r6s2ojZHlM4aVGJHcmaza/nrxkOhRANCAAQB9eScNYguhCJZfETKVsvF/xdqYwfD
      |GznucVJ4kvdVz2zDIu5fpI63KRG+5Y6rf6GPbHnR5eHM8q6u7Dv7QKR0
      |-----END PRIVATE KEY-----
      |""".stripMargin

  val clientPem: String =
    """-----BEGIN CERTIFICATE-----
      |MIIBujCCAWCgAwIBAgIBAzAKBggqhkjOPQQDAjAZMRcwFQYDVQQDDA5odHRwNHMg
      |dGVzdCBDQTAgFw0yNjAxMDEwMDAwMDBaGA8yMTI2MDEwMTAwMDAwMFowHTEbMBkG
      |A1UEAwwSaHR0cDRzIHRlc3QgY2xpZW50MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcD
      |QgAE1mRN6dgdsMbH4+INJ29eHNjTkoU8EGOVwKC8KtB8C1qh8Lx1PgT4xKUBvJmD
      |GiBVC4HFBJyiKk76gQJu57Pl6aOBkjCBjzAMBgNVHRMBAf8EAjAAMA4GA1UdDwEB
      |/wQEAwIHgDATBgNVHSUEDDAKBggrBgEFBQcDAjAaBgNVHREEEzARgglsb2NhbGhv
      |c3SHBH8AAAEwHQYDVR0OBBYEFPJNn4Y1vAFn9YtkK/6aZJwI9w0QMB8GA1UdIwQY
      |MBaAFMK9crCKisERlB7HpAWcS0GfYLVkMAoGCCqGSM49BAMCA0gAMEUCIHZlTH9t
      |ViQjzdQ1SyganA+6lUb3SPVurVX8hu8qlwOiAiEA33gmXgyjfmPpMUCTVIOcv+Va
      |qwEi7N9aVGG8pz/+tAU=
      |-----END CERTIFICATE-----
      |""".stripMargin

  val clientKeyPem: String =
    """-----BEGIN PRIVATE KEY-----
      |MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgLMLtDLWZQ1E5CTG0
      |zGs/hAeRe6J9uJXljuQkI7HtWTuhRANCAATWZE3p2B2wxsfj4g0nb14c2NOShTwQ
      |Y5XAoLwq0HwLWqHwvHU+BPjEpQG8mYMaIFULgcUEnKIqTvqBAm7ns+Xp
      |-----END PRIVATE KEY-----
      |""".stripMargin

  val untrustedClientPem: String =
    """-----BEGIN CERTIFICATE-----
      |MIIBpTCCAUugAwIBAgIBBDAKBggqhkjOPQQDAjAiMSAwHgYDVQQDDBdodHRwNHMg
      |dW50cnVzdGVkIGNsaWVudDAgFw0yNjAxMDEwMDAwMDBaGA8yMTI2MDEwMTAwMDAw
      |MFowIjEgMB4GA1UEAwwXaHR0cDRzIHVudHJ1c3RlZCBjbGllbnQwWTATBgcqhkjO
      |PQIBBggqhkjOPQMBBwNCAATPB5jFe7gMOehrDlc6ADt+4qbWtGbKkXVynxeAtWDr
      |3uyZNtDSNBmeXFWSmrIyyFWDRDfxAB039efQsYz1qGESo3AwbjAMBgNVHRMBAf8E
      |AjAAMA4GA1UdDwEB/wQEAwIHgDATBgNVHSUEDDAKBggrBgEFBQcDAjAaBgNVHREE
      |EzARgglsb2NhbGhvc3SHBH8AAAEwHQYDVR0OBBYEFIgqFdj7/ajXIzucJMQqZ7gJ
      |16NEMAoGCCqGSM49BAMCA0gAMEUCIQDkpOj+LFsBKaC9CdV39e4qFM+bQ0jXbEA8
      |/DRkdx3l2AIgZlw9xRvSSIHZR+a58v58+WAYGe+QUFOA+VpZyiuQSFA=
      |-----END CERTIFICATE-----
      |""".stripMargin

  val untrustedClientKeyPem: String =
    """-----BEGIN PRIVATE KEY-----
      |MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQguIdi9Wx9UF2VH4/W
      |mdAPF9yzFnE9nOVHGMo6jjKY4JahRANCAATPB5jFe7gMOehrDlc6ADt+4qbWtGbK
      |kXVynxeAtWDr3uyZNtDSNBmeXFWSmrIyyFWDRDfxAB039efQsYz1qGES
      |-----END PRIVATE KEY-----
      |""".stripMargin

  /** The DER bytes of a PEM certificate or key. */
  def der(pem: String): ByteVector =
    ByteVector.fromValidBase64(pem.replaceAll("-----(BEGIN|END) [A-Z ]+-----|\\s", ""))
}
