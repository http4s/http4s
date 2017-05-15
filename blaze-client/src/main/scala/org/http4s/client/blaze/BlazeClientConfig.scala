package org.http4s
package client
package blaze

import java.nio.channels.AsynchronousChannelGroup
import java.nio.charset.{ Charset => NioCharset, StandardCharsets }
import java.util.concurrent.ExecutorService
import javax.net.ssl.SSLContext

import org.http4s.headers.`User-Agent`
import org.http4s.parser.RequestUriParser
import org.http4s.syntax.string._

import scala.concurrent.duration.Duration
import scala.util.Try
import scala.util.matching.Regex

/** Config object for the blaze clients
  *
  * @param idleTimeout duration that a connection can wait without traffic before timeout
  * @param requestTimeout maximum duration for a request to complete before a timeout
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
                                   group: Option[AsynchronousChannelGroup],

                                   proxy: PartialFunction[RequestKey, ProxyConfig]
) {
  @deprecated("Parameter has been renamed to `checkEndpointIdentification`", "0.16")
  def endpointAuthentication: Boolean = checkEndpointIdentification

  def withProxy(pf: PartialFunction[RequestKey, ProxyConfig]) =
    copy(proxy = pf)
}

object BlazeClientConfig {
  /** Default configuration of a blaze client. */
  val defaultConfig =
    BlazeClientConfig(
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
      group = None,

      proxy = systemPropertiesProxyConfig
    )

  /**
   * Creates an SSLContext that trusts all certificates and disables
   * endpoint identification.  This is convenient in some development
   * environments for testing with untrusted certificates, but is
   * not recommended for production use.
   */
  val insecure: BlazeClientConfig =
    defaultConfig.copy(sslContext = Some(bits.TrustingSslContext), checkEndpointIdentification = false)

  def systemPropertiesProxyConfig: PartialFunction[RequestKey, ProxyConfig] = {
    type ProxyConfigPf = PartialFunction[RequestKey, ProxyConfig]

    val nonProxyHosts = sys.props.get("http.nonProxyHosts") match {
      case Some(nph) =>
        nph.split("|").toList.map { host =>
          new Regex(Regex.quote(host.replaceAllLiterally("*", ".*"))).pattern
        }
      case None => Nil
    }

    def skipProxy(authority: Uri.Authority) =
      nonProxyHosts.exists(_.matcher(authority.host.toString).matches)

    val httpConfig = for {
      rawHost <- sys.props.get("http.proxyHost")
      host <- new RequestUriParser(rawHost, StandardCharsets.UTF_8).Host.run().toOption
      rawPort <- sys.props.get("http.proxyPort").orElse(Some("80"))
      port <- Try(rawPort.toInt).toOption
    } yield ProxyConfig("http".ci, host, port, None)
    val httpConfigPf: ProxyConfigPf =
      httpConfig.fold(PartialFunction.empty: ProxyConfigPf) { cfg => {
        case RequestKey(scheme, authority)
            if scheme == "http".ci
            && !skipProxy(authority) =>
          cfg
      }}

    val httpsConfig = for {
      rawHost <- sys.props.get("https.proxyHost")
      host <- new RequestUriParser(rawHost, StandardCharsets.UTF_8).Host.run().toOption
      rawPort <- sys.props.get("https.proxyPort").orElse(Some("443"))
      port <- Try(rawPort.toInt).toOption
    } yield ProxyConfig("https".ci, host, port, None)
    val httpsConfigPf: ProxyConfigPf =
      httpsConfig.fold(PartialFunction.empty: ProxyConfigPf) { cfg => {
        case RequestKey(scheme, authority)
            if scheme == "https".ci
            && !skipProxy(authority) =>
          cfg
      }}

    httpConfigPf orElse httpsConfigPf
  }
}
