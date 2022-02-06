
#Versions

## Release lifecycle

* @:style(version-label, dev)Milestone@:@ releases are published for early adopters who need the latest dependencies or new features.  
  We will try to deprecate responsibly, but no binary compatibility is guaranteed.
* @:style(version-label, stable)Stable@:@ releases are recommended for production use. 
  Backward binary compatibility is preserved across the minor version. 
  Patches will be released for bugs, or selectively for backports deemed low risk and high value.
* @:style(version-label, eol)EOL@:@ releases are no longer supported by the http4s team. 
  Users will be advised to upgrade in the official support channels. 
  Patches may be released with a working pull request accompanied by a tale of woe.

## Which version is right for me?

* _I'll upgrade to Scala 3 before Cats-Effect 3:_ ${version.http4s.latest.0.22}
* _I'm ready for Cats-Effect 3:_ ${version.http4s.latest.0.23}
* _I'm new here, pick one:_ ${version.http4s.latest.0.23}
* _I'm using Scala.js [serverless](https://github.com/typelevel/feral) or in the [browser](https://http4s.github.io/http4s-dom):_ ${version.http4s.latest.0.23}
* _I live on the bleeding edge:_ ${version.http4s.latest.1.0}


| http4s                                   | Status                                  | Scala 2.11    | Scala 2.12    | Scala 2.13    | Scala 3       | Scala.js 1.x  | cats | fs2 | JDK |
| ---------------------------------------- | --------------------------------------- | ------------- | ------------- | ------------- | ------------- | ------------- | --- | --- | ---- |
| [${version.http4s.latest.1.0}](/v1.0/)   | @:style(version-label, dev)Milestone@:@ | @:icon(error) | @:icon(check) | @:icon(check) | 3.1           | 1.8           | 2.x | 3.x | 1.8+ |
| [${version.http4s.latest.0.23}](/v0.23/) | @:style(version-label, stable)Stable@:@ | @:icon(error) | @:icon(check) | @:icon(check) | 3.1           | 1.8           | 2.x | 3.x | 1.8+ |
| [${version.http4s.latest.0.22}](/v0.22/) | @:style(version-label, stable)Stable@:@ | @:icon(error) | @:icon(check) | @:icon(check) | 3.0           | @:icon(error) | 2.x | 2.x | 1.8+ |
| [${version.http4s.latest.0.21}](/v0.21/) | @:style(version-label, eol)EOL@:@       | @:icon(error) | @:icon(check) | @:icon(check) | @:icon(error) | @:icon(error) | 2.x | 2.x | 1.8+ |
| ${version.http4s.latest.0.20}            | @:style(version-label, eol)EOL@:@       | @:icon(check) | @:icon(check) | @:icon(error) | @:icon(error) | @:icon(error) | 1.x | 1.x | 1.8+ |
| ${version.http4s.latest.0.18}            | @:style(version-label, eol)EOL@:@       | @:icon(check) | @:icon(check) | @:icon(error) | @:icon(error) | @:icon(error) | 1.x | 0.10.x | 1.8+ |
| ${version.http4s.latest.0.17}            | @:style(version-label, eol)EOL@:@       | @:icon(check) | @:icon(check) | @:icon(error) | @:icon(error) | @:icon(error) | 0.9.x | 0.9.x | 1.8+ |
