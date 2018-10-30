package fix

import scalafix.v1._

import scala.meta._

sealed trait ClientType
object ClientType {

  case object Stream extends ClientType

  case object Apply extends ClientType
}

object ClientRules {
  def unapply(t: Tree)(implicit doc: SemanticDocument): Option[Patch] = t match {
    // Client builders
    case Importee.Name(c@Name("Http1Client")) =>
      Some(Patch.replaceTree(c, "BlazeClientBuilder"))
    case d@Defn.Def(_, _, _, _, _, c@Term.Apply(Term.ApplyType(Term.Name("Http1Client"), tpes), configParam)) =>
      Some(patchClient(d, c, configParam, tpes, ClientType.Apply))
    case d@Defn.Val(_, _, _, c@Term.Apply(Term.ApplyType(Term.Name("Http1Client"), tpes), configParam)) =>
      Some(patchClient(d, c, configParam, tpes, ClientType.Apply))
    case d@Defn.Var(_, _, _, c@Term.Apply(Term.ApplyType(Term.Name("Http1Client"), tpes), configParam)) =>
      Some(patchClient(d, c, configParam, tpes, ClientType.Apply))
    case d@Defn.Def(_, _, _, _, _, c@Term.Apply(Term.ApplyType(Term.Select(Term.Name("Http1Client"), Term.Name("stream")), tpes), configParam)) =>
      Some(patchClient(d, c, configParam, tpes, ClientType.Stream))
    case d@Defn.Val(_, _, _, c@Term.Apply(Term.ApplyType(Term.Select(Term.Name("Http1Client"), Term.Name("stream")), tpes), configParam)) =>
      Some(patchClient(d, c, configParam, tpes, ClientType.Stream))
    case d@Defn.Var(_, _, _, c@Term.Apply(Term.ApplyType(Term.Select(Term.Name("Http1Client"), Term.Name("stream")), tpes), configParam)) =>
      Some(patchClient(d, c, configParam, tpes, ClientType.Stream))
    case _ => None

  }

  private def patchClient(defn: Defn, client: Term, configParam: List[Term], tpes: List[Type], clientType: ClientType)(implicit doc: SemanticDocument) = {
    val configParams = getClientConfigParams(configParam)
    val ec = configParams.getOrElse("executionContext", Term.Name("global"))
    val sslc = configParams.get("sslContext")
    val newClientBuilder = Term.Apply(Term.ApplyType(Term.Name("BlazeClientBuilder"), tpes), List(Some(ec), sslc).flatten)
    val withParams = withConfigParams(configParams).map(p => Patch.addRight(client, s"$p\n"))
    Patch.replaceTree(client, newClientBuilder.toString()) ++ withParams + applyClientType(defn, client, clientType) + (ec match {
      case Term.Name("global") =>
        Patch.addGlobalImport(importer"scala.concurrent.ExecutionContext.Implicits.global")
      case _ =>
        Patch.empty
    })
  }


  private[this] def applyClientType(defn: Defn, client: Tree, clientType: ClientType): Patch = clientType match {
    case ClientType.Stream => Patch.addRight(client, ".stream")
    case ClientType.Apply =>
      Patch.addRight(client, ".resource") + replaceType(defn)
  }

  private def replaceType(defn: Defn): Patch =
    defn match {
      case Defn.Val(_, _, tpe, _) =>
        tpe.map(replaceClientType).asPatch
      case d@Defn.Var(_, _, tpe, _) =>
        tpe.map(replaceClientType).asPatch
      case d@Defn.Def(_, _, _, _, tpe, _) =>
        tpe.map(replaceClientType).asPatch
      case d =>
        Patch.lint(Diagnostic("1", s"Your client definition $defn needs to be replaced", d.pos))
    }

  private def replaceClientType(t: Type): Patch = t match {
    case t@t"$a[Client[$b]]" => Patch.replaceTree(t, s"cats.effect.Resource[$a, Client[$b]]")
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