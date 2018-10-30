package fix

import scalafix.v1._

import scala.meta._

sealed trait ClientType
object ClientType {

  case object Stream extends ClientType

  case object Resource extends ClientType
}

object ClientRules {
  def unapply(t: Tree)(implicit doc: SemanticDocument): Option[Patch] = t match {
    // Client builders
    case Importee.Name(c@Name("Http1Client")) =>
      Some(Patch.replaceTree(c, "BlazeClientBuilder"))
    case c@Term.Apply(Term.ApplyType(Term.Name("Http1Client"), tpes), configParam) =>
      Some(patchClient(c, configParam, tpes, ClientType.Resource))
    case d@Defn.Def(_, _, _, _, _, c@Term.Apply(Term.ApplyType(Term.Select(Term.Name("Http1Client"), Term.Name("stream")), tpes), configParam)) =>
      Some(patchClient(c, configParam, tpes, ClientType.Stream))
    case d@Defn.Val(_, _, _, c@Term.Apply(Term.ApplyType(Term.Select(Term.Name("Http1Client"), Term.Name("stream")), tpes), configParam)) =>
      Some(patchClient(c, configParam, tpes, ClientType.Stream))
    case d@Defn.Var(_, _, _, c@Term.Apply(Term.ApplyType(Term.Select(Term.Name("Http1Client"), Term.Name("stream")), tpes), configParam)) =>
      Some(patchClient(c, configParam, tpes, ClientType.Stream))
    case _ => None

  }

  private def patchClient(client: Term, configParam: List[Term], tpes: List[Type], clientType: ClientType)(implicit doc: SemanticDocument) = {
    val configParams = getClientConfigParams(configParam)
    val ec = configParams.getOrElse("executionContext", Term.Name("global"))
    val sslc = configParams.get("sslContext")
    val newClientBuilder = Term.Apply(Term.ApplyType(Term.Name("BlazeClientBuilder"), tpes), List(Some(ec), sslc).flatten)
    val withParams = withConfigParams(configParams).map(p => Patch.addRight(client, s"$p\n"))
    Patch.replaceTree(client, newClientBuilder.toString()) ++ withParams + applyClientType(client, clientType) + (ec match {
      case Term.Name("global") =>
        Patch.addGlobalImport(importer"scala.concurrent.ExecutionContext.Implicits.global")
      case _ =>
        Patch.empty
    })
  }


  private[this] def applyClientType(client: Tree, clientType: ClientType): Patch = clientType match {
    case ClientType.Stream => Patch.addRight(client, ".stream")
    case _ => Patch.empty
  }

  private[this] def getClientConfigParams(params: List[Term])(implicit doc: SemanticDocument) =
    params.headOption.flatMap {
      case c: Term.Name =>
        doc.tree.collect {
          case Defn.Val(_, pats, _, rhs) if isClientConfig(c, pats) =>
            rhs
        }.headOption
    } match {
      case Some(Term.Apply(_, ps)) =>
        ps.collect{
          case Term.Assign(Term.Name(name: String), p: Term) =>
            name -> p
        }.toMap
      case _ => Map.empty[String, Term]
    }


  private[this] def withConfigParams(params: Map[String, Term]): List[String] =
    params.flatMap{
      case ("lenientParser", Lit(v: Boolean)) =>
        if(v) Some(s".withParserMode(org.http4s.client.blaze.ParserMode.Lenient)")
        else Some(s".withParserMode(org.http4s.client.blaze.ParserMode.Strict)")
      case ("userAgent", Term.Apply(_, agent)) =>
        Some(s".withUserAgent(${agent.mkString})")
      case ("checkEndpointIdentification", Lit(v: Boolean)) =>
        Some(s".withCheckEndpointAuthentication($v)")
      case ("group", Term.Apply(_, group)) =>
        Some(s".withAsynchronousChannelGroup($group)")
      case (s, term) if s != "executionContext" && s != "sslContext" && s != "group"=>
        Some(s".with${s.head.toUpper}${s.tail}($term)")
      case _ => None
    }.toList

  private[this] def isClientConfig(configName: Term.Name, pats: List[Pat]) =
    pats.exists{
      case Pat.Var(Term.Name(name)) => name == configName.value
      case _ => false
    }

}