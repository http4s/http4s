package org.lyranthe.grpc.java_runtime.gen

import com.google.protobuf.Descriptors.{MethodDescriptor, ServiceDescriptor}
import scalapb.compiler.FunctionalPrinter.PrinterEndo
import scalapb.compiler.{DescriptorPimps, FunctionalPrinter, GeneratorParams, StreamType}

class Fs2GrpcServicePrinter(service: ServiceDescriptor) extends DescriptorPimps {
  private[this] def serviceMethodSignature(method: MethodDescriptor) = {
    s"def ${method.name}" + (method.streamType match {
      case StreamType.Unary =>
        s"(request: ${method.scalaIn}, clientHeaders: _root_.scala.Option[_root_.io.grpc.Metadata] = _root_.scala.None): F[${method.scalaOut}]"
      case StreamType.ClientStreaming =>
        s"(request: _root_.fs2.Stream[F, ${method.scalaIn}], clientHeaders: _root_.scala.Option[_root_.io.grpc.Metadata] = _root_.scala.None): F[${method.scalaOut}]"
      case StreamType.ServerStreaming =>
        s"(request: ${method.scalaIn}, clientHeaders: _root_.scala.Option[_root_.io.grpc.Metadata] = _root_.scala.None): _root_.fs2.Stream[F, ${method.scalaOut}]"
      case StreamType.Bidirectional =>
        s"(request: _root_.fs2.Stream[F, ${method.scalaIn}], clientHeaders: _root_.scala.Option[_root_.io.grpc.Metadata] = _root_.scala.None): _root_.fs2.Stream[F, ${method.scalaOut}]"
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
      s"_root_.org.lyranthe.grpc.java_runtime.client.Fs2ClientCall[F](channel, _root_.${service.getFile.scalaPackageName}.GreeterGrpc.${method.descriptorName}, callOptions)"
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

  private[this] def serviceMethods: PrinterEndo = _.seq(service.methods.map(serviceMethodSignature))

  private[this] def serviceMethodImplementations: PrinterEndo =
    _.call(service.methods.map(serviceMethodImplementation): _*)

  private[this] def serviceTrait: PrinterEndo =
    _.add(s"trait ${service.name}[F[_]] {").indent.call(serviceMethods).outdent.add("}")

  private[this] def serviceObject: PrinterEndo =
    _.add(s"object ${service.name} {").indent.call(serviceClient).outdent.add("}")

  private[this] def serviceClient: PrinterEndo = {
    _.add(
      s"def stub[F[_]: _root_.cats.effect.Effect](channel: _root_.io.grpc.Channel, callOptions: _root_.io.grpc.CallOptions = _root_.io.grpc.CallOptions.DEFAULT)(implicit ec: _root_.scala.concurrent.ExecutionContext): ${service.name}[F] = new ${service.name}[F] {").indent
      .call(serviceMethodImplementations)
      .outdent
      .add("}")
  }

  def printService(printer: FunctionalPrinter): FunctionalPrinter = {
    printer
      .add("package " + service.getFile.scalaPackageName + ".fs2", "", "import _root_.cats.implicits._", "")
      .call(serviceTrait)
      .call(serviceObject)
  }

  // Not used, but required by DescriptorPimps
  override def params: GeneratorParams = GeneratorParams()
}
