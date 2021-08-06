sealed abstract class JSEnv
object JSEnv {
  case object Chrome extends JSEnv
  case object Firefox extends JSEnv
  case object NodeJS extends JSEnv
}
