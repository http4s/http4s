package org.http4s.client
package blaze

import java.nio.channels.AsynchronousChannelGroup
import java.util.concurrent.ExecutorService
import javax.net.ssl.SSLContext

import org.http4s.headers.`User-Agent`

import scala.concurrent.duration.Duration

/** Config object for the blaze clients
  *
  * @param responseHeaderTimeout duration between the completion of a request
  * and the completion of the response header.  Does not include time
  * to acquire the connection or the time to read the response.
  * @param idleTimeout duration that a connection can wait without
  * traffic being read or written before timeout
  * @param requestTimeout maximum duration for a request to complete
  * before a timeout.  Does not include time to acquire the the
  * connection, but does include time to read the response
  * @param userAgent optional custom user agent header
  * @param sslContext optional custom `SSLContext` to use to replace
  * the default, `SSLContext.getDefault`.
  * @param checkEndpointIdentification require endpoint identification
  * for secure requests according to RFC 2818, Section 3.1.  If the
  * certificate presented does not match the hostname of the request,
  * the request fails with a CertificateException.  This setting does
  * not affect checking the validity of the cert via the
  * `sslContext`'s trust managers.
  * @param maxResponseLineSize maximum length of the request line
  * @param maxHeaderLength maximum length of headers
  * @param maxChunkSize maximum size of chunked content chunks
  * @param lenientParser a lenient parser will accept illegal chars but replaces them with � (0xFFFD)
  * @param bufferSize internal buffer size of the blaze client
  * @param customExecutor custom executor to run async computations. Will not be shutdown with client.
  * @param group custom `AsynchronousChannelGroup` to use other than the system default
  */
final case class BlazeClientConfig(// HTTP properties
                                   responseHeaderTimeout: Duration,
                                   idleTimeout: Duration,
                                   requestTimeout: Duration,
                                   userAgent: Option[`User-Agent`],

                                   // security options
                                   sslContext: Option[SSLContext],
                                   @deprecatedName('endpointAuthentication) checkEndpointIdentification: Boolean,

                                   // parser options
                                   maxResponseLineSize: Int,
                                   maxHeaderLength: Int,
                                   maxChunkSize: Int,
                                   lenientParser: Boolean,

                                   // pipeline management
                                   bufferSize: Int,
                                   customExecutor: Option[ExecutorService],
                                   group: Option[AsynchronousChannelGroup]
) {
  @deprecated("Parameter has been renamed to `checkEndpointIdentification`", "0.16")
  def endpointAuthentication: Boolean = checkEndpointIdentification
}

object BlazeClientConfig {
  /** Default configuration of a blaze client. */
  val defaultConfig =
    BlazeClientConfig(
      responseHeaderTimeout = bits.DefaultResponseHeaderTimeout,
      idleTimeout = bits.DefaultTimeout,
      requestTimeout = Duration.Inf,
      userAgent = bits.DefaultUserAgent,

      sslContext = None,
      checkEndpointIdentification = true,

      maxResponseLineSize = 4*1024,
      maxHeaderLength = 40*1024,
      maxChunkSize = Integer.MAX_VALUE,
      lenientParser = false,

      bufferSize = bits.DefaultBufferSize,
      customExecutor = None,
      group = None
    )

  /**
   * Creates an SSLContext that trusts all certificates and disables
   * endpoint identification.  This is convenient in some development
   * environments for testing with untrusted certificates, but is
   * not recommended for production use.
   */
  val insecure: BlazeClientConfig =
    defaultConfig.copy(sslContext = Some(bits.TrustingSslContext), checkEndpointIdentification = false)
}
