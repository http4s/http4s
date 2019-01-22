package org.http4s.client
package blaze

import java.nio.channels.AsynchronousChannelGroup
import javax.net.ssl.SSLContext
import org.http4s.headers.`User-Agent`
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/** Config object for the blaze clients
  *
  * @param responseHeaderTimeout duration between the submission of a
  * request and the completion of the response header.  Does not
  * include time to read the response body.
  * @param idleTimeout duration that a connection can wait without
  * traffic being read or written before timeout
  * @param requestTimeout maximum duration from the submission of a
  * request through reading the body before a timeout.
  * @param userAgent optional custom user agent header
  * @param maxTotalConnections maximum connections the client will have at any specific time
  * @param maxWaitQueueLimit maximum number requests waiting for a connection at any specific time
  * @param maxConnectionsPerRequestKey Map of RequestKey to number of max connections
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
  * @param chunkBufferMaxSize Size of the buffer that is used when Content-Length header is not specified.
  * @param lenientParser a lenient parser will accept illegal chars but replaces them with � (0xFFFD)
  * @param bufferSize internal buffer size of the blaze client
  * @param executionContext custom executionContext to run async computations.
  * @param group custom `AsynchronousChannelGroup` to use other than the system default
  */
@deprecated("Use BlazeClientBuilder", "0.19.0-M2")
final case class BlazeClientConfig( // HTTP properties
    responseHeaderTimeout: Duration,
    idleTimeout: Duration,
    requestTimeout: Duration,
    userAgent: Option[`User-Agent`],
    // pool options
    maxTotalConnections: Int,
    maxWaitQueueLimit: Int,
    maxConnectionsPerRequestKey: RequestKey => Int,
    // security options
    sslContext: Option[SSLContext],
    @deprecatedName('endpointAuthentication) checkEndpointIdentification: Boolean,
    // parser options
    maxResponseLineSize: Int,
    maxHeaderLength: Int,
    maxChunkSize: Int,
    chunkBufferMaxSize: Int,
    lenientParser: Boolean,
    // pipeline management
    bufferSize: Int,
    executionContext: ExecutionContext,
    group: Option[AsynchronousChannelGroup]) {
  @deprecated("Parameter has been renamed to `checkEndpointIdentification`", "0.16")
  def endpointAuthentication: Boolean = checkEndpointIdentification
}

@deprecated("Use BlazeClientBuilder", "0.19.0-M2")
object BlazeClientConfig {

  /** Default configuration of a blaze client. */
  val defaultConfig =
    BlazeClientConfig(
      responseHeaderTimeout = bits.DefaultResponseHeaderTimeout,
      idleTimeout = bits.DefaultTimeout,
      requestTimeout = 1.minute,
      userAgent = bits.DefaultUserAgent,
      maxTotalConnections = bits.DefaultMaxTotalConnections,
      maxWaitQueueLimit = bits.DefaultMaxWaitQueueLimit,
      maxConnectionsPerRequestKey = _ => bits.DefaultMaxTotalConnections,
      sslContext = None,
      checkEndpointIdentification = true,
      maxResponseLineSize = 4 * 1024,
      maxHeaderLength = 40 * 1024,
      maxChunkSize = Integer.MAX_VALUE,
      chunkBufferMaxSize = 1024 * 1024,
      lenientParser = false,
      bufferSize = bits.DefaultBufferSize,
      executionContext = ExecutionContext.global,
      group = None
    )

  /**
    * Creates an SSLContext that trusts all certificates and disables
    * endpoint identification.  This is convenient in some development
    * environments for testing with untrusted certificates, but is
    * not recommended for production use.
    */
  val insecure: BlazeClientConfig =
    defaultConfig.copy(
      sslContext = Some(bits.TrustingSslContext),
      checkEndpointIdentification = false)
}
