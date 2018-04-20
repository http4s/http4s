package org.lyranthe.fs2_grpc.java_runtime.sbt_gen

import com.google.protobuf.Descriptors.{MethodDescriptor, ServiceDescriptor}
import scalapb.compiler.FunctionalPrinter.PrinterEndo
import scalapb.compiler.{DescriptorPimps, FunctionalPrinter, GeneratorParams, StreamType}

class Fs2GrpcServicePrinter(service: ServiceDescriptor, override val params: GeneratorParams) extends DescriptorPimps {
  private[this] def serviceMethodSignature(method: MethodDescriptor) = {
    s"def ${method.name}" + (method.streamType match {
      case StreamType.Unary =>
        s"(request: ${method.scalaIn}, clientHeaders: _root_.io.grpc.Metadata): F[${method.scalaOut}]"
      case StreamType.ClientStreaming =>
        s"(request: _root_.fs2.Stream[F, ${method.scalaIn}], clientHeaders: _root_.io.grpc.Metadata): F[${method.scalaOut}]"
      case StreamType.ServerStreaming =>
        s"(request: ${method.scalaIn}, clientHeaders: _root_.io.grpc.Metadata): _root_.fs2.Stream[F, ${method.scalaOut}]"
      case StreamType.Bidirectional =>
        s"(request: _root_.fs2.Stream[F, ${method.scalaIn}], clientHeaders: _root_.io.grpc.Metadata): _root_.fs2.Stream[F, ${method.scalaOut}]"
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
      s"_root_.org.lyranthe.fs2_grpc.java_runtime.client.Fs2ClientCall[F](channel, _root_.${service.getFile.scalaPackageName}.${service.name}Grpc.${method.descriptorName}, callOptions)"
    if (method.isServerStreaming)
      s"_root_.fs2.Stream.eval($basicClientCall)"
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
      s".addMethod(_root_.${service.getFile.scalaPackageName}.${service.getName}Grpc.${method.descriptorName}, _root_.org.lyranthe.fs2_grpc.java_runtime.server.Fs2ServerCallHandler[F].${handleMethod(
        method)}(serviceImpl.${method.name}))")
  }

  private[this] def serviceMethods: PrinterEndo = _.seq(service.methods.map(serviceMethodSignature))

  private[this] def serviceMethodImplementations: PrinterEndo =
    _.call(service.methods.map(serviceMethodImplementation): _*)

  private[this] def serviceBindingImplementations: PrinterEndo =
    _.indent
      .add(s".builder(_root_.${service.getFile.scalaPackageName}.${service.getName}Grpc.${service.descriptorName})")
      .call(service.methods.map(serviceBindingImplementation): _*)
      .add(".build()")
      .outdent

  private[this] def serviceTrait: PrinterEndo =
    _.add(s"trait ${service.name}Fs2Grpc[F[_]] {").indent.call(serviceMethods).outdent.add("}")

  private[this] def serviceObject: PrinterEndo =
    _.add(s"object ${service.name}Fs2Grpc {").indent.call(serviceClient).call(serviceBinding).outdent.add("}")

  private[this] def serviceClient: PrinterEndo = {
    _.add(
      s"def stub[F[_]: _root_.cats.effect.Effect](channel: _root_.io.grpc.Channel, callOptions: _root_.io.grpc.CallOptions = _root_.io.grpc.CallOptions.DEFAULT)(implicit ec: _root_.scala.concurrent.ExecutionContext): ${service.name}Fs2Grpc[F] = new ${service.name}Fs2Grpc[F] {").indent
      .call(serviceMethodImplementations)
      .outdent
      .add("}")
  }

  private[this] def serviceBinding: PrinterEndo = {
    _.add(
      s"def bindService[F[_]: _root_.cats.effect.Effect](serviceImpl: ${service.name}Fs2Grpc[F])(implicit ec: _root_.scala.concurrent.ExecutionContext): _root_.io.grpc.ServerServiceDefinition = {").indent
      .add("_root_.io.grpc.ServerServiceDefinition")
      .call(serviceBindingImplementations)
      .outdent
      .add("}")
  }

  def printService(printer: FunctionalPrinter): FunctionalPrinter = {
    printer
      .add("package " + service.getFile.scalaPackageName, "", "import _root_.cats.implicits._", "")
      .call(serviceTrait)
      .call(serviceObject)
  }
}
