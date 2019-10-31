package java.net

case class InetAddress(hostAddress: String) {
  def getHostAddress: String = hostAddress
  def getHostName: String = hostAddress
}

object InetAddress {
  def getLoopbackAddress: InetAddress = InetAddress("127.0.0.1")
  def getLocalHost: InetAddress = InetAddress("127.0.0.1")
  def getByName(adress: String): InetAddress = InetAddress(adress)
}

case class InetSocketAddress(adress: InetAddress, port: Int) {
  def getPort: Int = port
  def getAddress: InetAddress = adress
  def getHostString: String = adress.hostAddress
  def getHostName: String = adress.hostAddress
}

object InetSocketAddress {
  def createUnresolved(host: String, port: Int): InetSocketAddress =
    new InetSocketAddress(InetAddress(host), checkPort(port))

  private def checkPort(port: Int) = {
    if (port < 0 || port > 0xFFFF) throw new IllegalArgumentException("port out of range:" + port)
    port
  }
}

object URLEncoder {
  def encode(s: String, enc: String): String = s + enc
}
