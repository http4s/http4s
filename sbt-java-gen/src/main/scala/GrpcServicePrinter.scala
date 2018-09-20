package org.lyranthe.fs2_grpc.java_runtime.sbt_gen

import com.google.protobuf.Descriptors.{MethodDescriptor, ServiceDescriptor}
import scalapb.compiler.FunctionalPrinter.PrinterEndo
import scalapb.compiler.{DescriptorImplicits, FunctionalPrinter, StreamType}

class Fs2GrpcServicePrinter(service: ServiceDescriptor, di: DescriptorImplicits){
  import di._
  import Fs2GrpcServicePrinter.constants._

  private[this] val serviceName: String    = service.name
  private[this] val servicePkgName: String = service.getFile.scalaPackageName

  private[this] def serviceMethodSignature(method: MethodDescriptor) = {

    val scalaInType   = method.inputType.scalaType
    val scalaOutType  = method.outputType.scalaType
    val clientHeaders = s"clientHeaders: $Metadata"

    s"def ${method.name}" + (method.streamType match {
      case StreamType.Unary           => s"(request: $scalaInType, $clientHeaders): F[$scalaOutType]"
      case StreamType.ClientStreaming => s"(request: $Stream[F, $scalaInType], $clientHeaders): F[$scalaOutType]"
      case StreamType.ServerStreaming => s"(request: $scalaInType, $clientHeaders): $Stream[F, $scalaOutType]"
      case StreamType.Bidirectional   => s"(request: $Stream[F, $scalaInType], $clientHeaders): $Stream[F, $scalaOutType]"
    })
  }

  private[this] def handleMethod(method: MethodDescriptor) = {
    method.streamType match {
      case StreamType.Unary           => "unaryToUnaryCall"
      case StreamType.ClientStreaming => "streamingToUnaryCall"
      case StreamType.ServerStreaming => "unaryToStreamingCall"
      case StreamType.Bidirectional   => "streamingToStreamingCall"
    }
  }

  private[this] def createClientCall(method: MethodDescriptor) = {
    val basicClientCall =
      s"$Fs2ClientCall[F](channel, _root_.$servicePkgName.${serviceName}Grpc.${method.descriptorName}, callOptions)"
    if (method.isServerStreaming)
      s"$Stream.eval($basicClientCall)"
    else
      basicClientCall
  }

  private[this] def serviceMethodImplementation(method: MethodDescriptor): PrinterEndo = { p =>
    p.add(serviceMethodSignature(method) + " = {")
      .indent
      .add(
        s"${createClientCall(method)}.flatMap(_.${handleMethod(method)}(request, clientHeaders))"
      )
      .outdent
      .add("}")
  }

  // TODO: update this
  private[this] def serviceBindingImplementation(method: MethodDescriptor): PrinterEndo = { p =>
    p.add(
      s".addMethod(_root_.$servicePkgName.${serviceName}Grpc.${method.descriptorName}, $Fs2ServerCallHandler[F].${handleMethod(
        method)}(serviceImpl.${method.name}))")
  }

  private[this] def serviceMethods: PrinterEndo = _.seq(service.methods.map(serviceMethodSignature))

  private[this] def serviceMethodImplementations: PrinterEndo =
    _.call(service.methods.map(serviceMethodImplementation): _*)

  private[this] def serviceBindingImplementations: PrinterEndo =
    _.indent
      .add(s".builder(_root_.$servicePkgName.${serviceName}Grpc.${service.descriptorName})")
      .call(service.methods.map(serviceBindingImplementation): _*)
      .add(".build()")
      .outdent

  private[this] def serviceTrait: PrinterEndo =
    _.add(s"trait ${serviceName}Fs2Grpc[F[_]] {").indent.call(serviceMethods).outdent.add("}")

  private[this] def serviceObject: PrinterEndo =
    _.add(s"object ${serviceName}Fs2Grpc {").indent.call(serviceClient).call(serviceBinding).outdent.add("}")

  private[this] def serviceClient: PrinterEndo = {
    _.add(
      s"def stub[F[_]: $ConcurrentEffect](channel: $Channel, callOptions: $CallOptions = $CallOptions.DEFAULT): ${serviceName}Fs2Grpc[F] = new ${serviceName}Fs2Grpc[F] {").indent
      .call(serviceMethodImplementations)
      .outdent
      .add("}")
  }

  private[this] def serviceBinding: PrinterEndo = {
    _.add(
      s"def bindService[F[_]: $ConcurrentEffect](serviceImpl: ${service.name}Fs2Grpc[F]): $ServerServiceDefinition = {").indent
      .add(s"$ServerServiceDefinition")
      .call(serviceBindingImplementations)
      .outdent
      .add("}")
  }

  def printService(printer: FunctionalPrinter): FunctionalPrinter = {
    printer
      .add(s"package $servicePkgName", "", "import _root_.cats.implicits._", "")
      .call(serviceTrait)
      .call(serviceObject)
  }
}

object Fs2GrpcServicePrinter {

  object constants {

    private val effPkg  = "_root_.cats.effect"
    private val grpcPkg = "_root_.io.grpc"
    private val jrtPkg  = "_root_.org.lyranthe.fs2_grpc.java_runtime"
    private val fs2Pkg  = "_root_.fs2"

    ///

    val ConcurrentEffect        = s"$effPkg.ConcurrentEffect"
    val Stream                  = s"$fs2Pkg.Stream"
    val Fs2ServerCallHandler    = s"$jrtPkg.server.Fs2ServerCallHandler"
    val Fs2ClientCall           = s"$jrtPkg.client.Fs2ClientCall"
    val ServerServiceDefinition = s"$grpcPkg.ServerServiceDefinition"
    val CallOptions             = s"$grpcPkg.CallOptions"
    val Channel                 = s"$grpcPkg.Channel"
    val Metadata                = s"$grpcPkg.Metadata"

  }

}
