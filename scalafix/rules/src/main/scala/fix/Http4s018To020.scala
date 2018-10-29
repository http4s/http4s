package fix

import scalafix.v1._
import scala.meta._

class Http4s018To020 extends SemanticRule("Http4s018To020") {

  val mimeMatcher = SymbolMatcher.normalized("org/http4s/MediaType#")

  override def fix(implicit doc: SemanticDocument): Patch = {
    doc.tree.collect{
      // HttpService -> HttpRoutes.of
      case t@Type.Name("HttpService") => Patch.replaceTree(t, "HttpRoutes")
      case t@Term.Name("HttpService") => Patch.replaceTree(t, "HttpRoutes.of")
      case t@Importee.Name(Name("HttpService")) => Patch.replaceTree(t, "HttpRoutes")

      // withBody -> withEntity
      case Defn.Val(_, _, tpe, rhs) if containsWithBody(rhs) =>
        replaceWithBody(rhs) ++ tpe.map(removeExternalF).toList
      case Defn.Def(_, _, _, _, tpe, rhs) if containsWithBody(rhs) =>
        replaceWithBody(rhs) ++ tpe.map(removeExternalF).toList
      case Defn.Var(_, _, tpe, rhs) if rhs.exists(containsWithBody) =>
        rhs.map(replaceWithBody).asPatch ++ tpe.map(removeExternalF).toList

      // cookies
      case Importer(Term.Select(Term.Name("org"), Term.Name("http4s")), is) =>
        is.collect{
          case c@Importee.Name(Name("Cookie")) =>
            Patch.addGlobalImport(Importer(Term.Select(Term.Name("org"), Term.Name("http4s")),
              List(Importee.Rename(Name("ResponseCookie"), Name("Cookie"))))) +
            Patch.removeImportee(c)
          case c@Importee.Rename(Name("Cookie"), rename) =>
            Patch.addGlobalImport(Importer(Term.Select(Term.Name("org"), Term.Name("http4s")),
              List(Importee.Rename(Name("ResponseCookie"), rename)))) +
              Patch.removeImportee(c)
        }.asPatch

      // MiMe types
      case Term.Select(mimeMatcher(t), media) =>
        val mediaParts = media.toString.replace("`", "").split("/").map{
          part =>
            if(!part.forall(c => c.isLetterOrDigit || c == '_'))
              s"`$part`"
            else
              part
        }
        Patch.replaceTree(media,
          mediaParts.mkString(".")
        )

      // Client builders
      case Importee.Name(c@Name("Http1Client")) =>
        Patch.replaceTree(c, "BlazeClientBuilder")
      case c@Term.Apply(Term.ApplyType(n@Term.Name("Http1Client"), tpes), configParam) =>
        val configParams = getClientConfigParams(configParam)
        val ec = configParams.getOrElse("executionContext", Term.Name("global"))
        val sslc = configParams.get("sslContext")
        Patch.replaceTree(c, s"BlazeClientBuilder[${tpes.mkString(", ")}]($ec${sslc.fold("")(s => s", $s")})${withConfigParams(configParams)}") + (ec match {
          case Term.Name("global") =>
            Patch.addGlobalImport(importer"scala.concurrent.ExecutionContext.Implicits.global")
          case _ =>
            Patch.empty
        })
      case d@Defn.Def(_, _, _, _, _, c@Term.Apply(Term.ApplyType(Term.Select(Term.Name("Http1Client"), Term.Name("stream")), tpes), configParam)) =>
        patchClient(d, c, configParam, tpes)
      case d@Defn.Val(_, _, _, c@Term.Apply(Term.ApplyType(Term.Select(Term.Name("Http1Client"), Term.Name("stream")), tpes), configParam)) =>
        patchClient(d, c, configParam, tpes)
      case d@Defn.Var(_, _, _, c@Term.Apply(Term.ApplyType(Term.Select(Term.Name("Http1Client"), Term.Name("stream")), tpes), configParam)) =>
        patchClient(d, c, configParam, tpes)
    }
  }.asPatch

  def patchClient(defn: Stat, client: Term, configParam: List[Term], tpes: List[Type])(implicit doc: SemanticDocument) = {
    val configParams = getClientConfigParams(configParam)
    val ec = configParams.getOrElse("executionContext", Term.Name("global"))
    val sslc = configParams.get("sslContext")
    Patch.replaceTree(client, s"BlazeClientBuilder[${tpes.mkString(", ")}]($ec${sslc.fold("")(s => s", $s")})${withConfigParams(configParams)}.stream") + (ec match {
      case Term.Name("global") =>
        Patch.addGlobalImport(importer"scala.concurrent.ExecutionContext.Implicits.global")
      case _ =>
        Patch.empty
    }) + (if(tpes.exists{ case Type.Name("IO") => true; case _ => false}) addIOContextShift(defn, ec) else Patch.empty)
  }


  def addIOContextShift(t: Tree, ec: Term) =
    Patch.addLeft(t, s"implicit val cs = IO.contextShift($ec)\n")

  def getClientConfigParams(params: List[Term])(implicit doc: SemanticDocument) =
    params.headOption.flatMap {
      case c: Term.Name =>
        doc.tree.collect {
          case Defn.Val(_, pats, _, rhs) if isClientConfig(c, pats) =>
            rhs
        }.headOption
    } match {
      case Some(Term.Apply(_, params)) =>
        params.collect{
          case Term.Assign(Term.Name(name: String), p: Term) =>
            name -> p
        }.toMap
      case _ => Map.empty[String, Term]
    }

  def withConfigParams(params: Map[String, Term]) =
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
    }.mkString("\n")

  def removeExternalF(t: Type) =
    t match {
      case r@t"$a[Request[$b]]" =>
        // Note: we only change type def in request and not in response as normally the responses created with
        // e.g. Ok() are still F[Response[F]]
        Patch.replaceTree(r, s"Request[$b]")
      case _ =>
        Patch.empty
    }

  def replaceWithBody(t: Tree) =
    t.collect{
      case Term.Select(p, t@Term.Name("withBody")) =>
        Patch.replaceTree(t, "withEntity")
    }.asPatch

  def containsWithBody(t: Tree): Boolean =
    t.collect {
      case Term.Select(_, Term.Name("withBody")) =>
        true
    }.contains(true)

  def isClientConfig(configName: Term.Name, pats: List[Pat]) =
    pats.exists{
      case Pat.Var(Term.Name(name)) => name == configName.value
      case _ => false
    }

}
