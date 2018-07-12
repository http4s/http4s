package org.lyranthe.fs2_grpc.java_runtime.sbt_gen

import com.google.protobuf.Descriptors.FileDescriptor
import com.google.protobuf.ExtensionRegistry
import com.google.protobuf.compiler.PluginProtos
import com.google.protobuf.compiler.PluginProtos.{CodeGeneratorRequest, CodeGeneratorResponse}
import org.lyranthe.fs2_grpc.java_runtime.sbt_gen.Fs2GrpcPlugin.autoImport.scalapbCodeGenerators
import protocbridge.{Artifact, JvmGenerator, ProtocCodeGenerator, Target}
import sbt._
import sbt.Keys._
import sbt.plugins.JvmPlugin
import sbtprotoc.ProtocPlugin.autoImport.PB
import scalapb.compiler.{FunctionalPrinter, GeneratorException, GeneratorParams, ProtobufGenerator}
import scalapb.options.compiler.Scalapb

import scala.collection.JavaConverters._

sealed trait CodeGeneratorOption extends Product with Serializable

object Fs2CodeGenerator extends ProtocCodeGenerator {

  def generateServiceFiles(file: FileDescriptor,
                           params: GeneratorParams): Seq[PluginProtos.CodeGeneratorResponse.File] = {
    println("Services: " + file.getServices.asScala)
    file.getServices.asScala.map { service =>
      val p = new Fs2GrpcServicePrinter(service, params)

      import p.{FileDescriptorPimp, ServiceDescriptorPimp}
      val code = p.printService(FunctionalPrinter()).result()
      val b    = CodeGeneratorResponse.File.newBuilder()
      b.setName(file.scalaDirectory + "/" + service.objectName + "Fs2.scala")
      b.setContent(code)
      println(b.getName)
      b.build
    }
  }

  def handleCodeGeneratorRequest(request: PluginProtos.CodeGeneratorRequest): PluginProtos.CodeGeneratorResponse = {
    val b = CodeGeneratorResponse.newBuilder
    ProtobufGenerator.parseParameters(request.getParameter) match {
      case Right(params) =>
        try {
          val filesByName: Map[String, FileDescriptor] =
            request.getProtoFileList.asScala.foldLeft[Map[String, FileDescriptor]](Map.empty) {
              case (acc, fp) =>
                val deps = fp.getDependencyList.asScala.map(acc)
                acc + (fp.getName -> FileDescriptor.buildFrom(fp, deps.toArray))
            }

          val files = request.getFileToGenerateList.asScala.map(filesByName).flatMap(generateServiceFiles(_, params))
          b.addAllFile(files.asJava)
        } catch {
          case e: GeneratorException =>
            b.setError(e.message)
        }

      case Left(error) =>
        b.setError(error)
    }

    b.build()
  }

  override def run(req: Array[Byte]): Array[Byte] = {
    println("Running")
    val registry = ExtensionRegistry.newInstance()
    Scalapb.registerAllExtensions(registry)
    val request = CodeGeneratorRequest.parseFrom(req, registry)
    handleCodeGeneratorRequest(request).toByteArray
  }

  override def suggestedDependencies: Seq[Artifact] = Seq(
    Artifact("com.thesamet.scalapb", "scalapb-runtime", scalapb.compiler.Version.scalapbVersion, crossVersion = true)
  )
}

object Fs2Grpc extends AutoPlugin {
  override def requires = Fs2GrpcPlugin
  override def trigger  = NoTrigger

  override def projectSettings: Seq[Def.Setting[_]] = List(
    PB.targets := scalapbCodeGenerators.value
  )
}

object Fs2GrpcPlugin extends AutoPlugin {
  object autoImport {
    val grpcJavaVersion: String = scalapb.compiler.Version.grpcJavaVersion

    object CodeGeneratorOption {
      case object FlatPackage extends CodeGeneratorOption {
        override def toString = "flat_package"
      }
      case object JavaConversions extends CodeGeneratorOption {
        override def toString: String = "java_conversions"
      }
      case object Grpc extends CodeGeneratorOption {
        override def toString: String = "grpc"
      }
      case object Fs2Grpc extends CodeGeneratorOption {
        override def toString: String = "fs2_grpc"
      }
      case object SingleLineToProtoString extends CodeGeneratorOption {
        override def toString: String = "single_line_to_proto_string"
      }
      case object AsciiFormatToString extends CodeGeneratorOption {
        override def toString: String = "ascii_format_to_string"
      }
    }

    val scalapbCodeGeneratorOptions =
      settingKey[Seq[CodeGeneratorOption]]("Settings for scalapb/fs2-grpc code generation")
    val scalapbProtobufDirectory =
      settingKey[File]("Directory containing protobuf files for scalapb")
    val scalapbCodeGenerators =
      settingKey[Seq[Target]]("Code generators for scalapb")
  }
  import autoImport._

  override def requires = sbtprotoc.ProtocPlugin && JvmPlugin
  override def trigger  = NoTrigger

  def convertOptionsToScalapbGen(options: Set[CodeGeneratorOption]): (JvmGenerator, Seq[String]) = {
    scalapb.gen(
      flatPackage = options(CodeGeneratorOption.FlatPackage),
      javaConversions = options(CodeGeneratorOption.JavaConversions),
      grpc = options(CodeGeneratorOption.Grpc),
      singleLineToProtoString = options(CodeGeneratorOption.SingleLineToProtoString),
      asciiFormatToString = options(CodeGeneratorOption.AsciiFormatToString)
    )
  }

  override def projectSettings: Seq[Def.Setting[_]] = List(
    scalapbProtobufDirectory := (sourceManaged in Compile).value / "scalapb",
    scalapbCodeGenerators := {
      Target(convertOptionsToScalapbGen(scalapbCodeGeneratorOptions.value.toSet),
             (sourceManaged in Compile).value / "scalapb") ::
        Option(
        Target(
          (JvmGenerator("scala-fs2-grpc", Fs2CodeGenerator),
           scalapbCodeGeneratorOptions.value.filterNot(_ == CodeGeneratorOption.Fs2Grpc).map(_.toString)),
          (sourceManaged in Compile).value / "fs2-grpc"
        ))
        .filter(_ => scalapbCodeGeneratorOptions.value.contains(CodeGeneratorOption.Fs2Grpc))
        .toList
    },
    scalapbCodeGeneratorOptions := Seq(CodeGeneratorOption.Grpc, CodeGeneratorOption.Fs2Grpc),
    libraryDependencies ++= List(
      "io.grpc"               % "grpc-core"             % scalapb.compiler.Version.grpcJavaVersion,
      "io.grpc"               % "grpc-stub"             % scalapb.compiler.Version.grpcJavaVersion,
      "org.lyranthe.fs2-grpc" %% "java-runtime"         % org.lyranthe.fs2_grpc.buildinfo.BuildInfo.version,
      "com.thesamet.scalapb"  %% "scalapb-runtime"      % scalapb.compiler.Version.scalapbVersion,
      "com.thesamet.scalapb"  %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion
    )
  )
}
