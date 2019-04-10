package tech.cryptonomic.conseil.generic.chain

trait RemoteRpc[Eff[_], Req[_], Res[_]] {

  type CallConfig
  type PostPayload

  def runGetCall[CallId](callConfig: CallConfig, request: Req[CallId], commandMap: CallId => String): Eff[Res[CallId]]

  def runPostCall[CallId](callConfig: CallConfig, request: Req[CallId], commandMap: CallId => String, payload: Option[PostPayload] = None): Eff[Res[CallId]]

}

object RemoteRpc {

  import cats.{Id, Functor}
  import cats.data.Const
  import cats.syntax.functor._

  type Aux[Eff[_], Req[_], Res[_], Conf, Payload] = RemoteRpc[Eff, Req, Res] { type CallConfig = Conf; type PostPayload = Payload }

  def apply[Eff[_], Req[_], Res[_], CallConfig, Payload](
    implicit remote: Aux[Eff, Req, Res, CallConfig, Payload]
  ): Aux[Eff, Req, Res, CallConfig, Payload] = remote

  def runGet[Eff[_], CallId, Req[_], Res[_], CallConfig](
    callConfig: CallConfig,
    request: Req[CallId],
    commandMap: CallId => String
  )(implicit remote: Aux[Eff, Req, Res, CallConfig, _]): Eff[Res[CallId]] =
    remote.runGetCall(callConfig, request, commandMap)

  def runGet[Eff[_], CallId, Req[_], Res[_]](
    request: Req[CallId],
    commandMap: CallId => String
  )(implicit remote: Aux[Eff, Req, Res, Any, _]): Eff[Res[CallId]] =
    remote.runGetCall((), request, commandMap)

  def runGet[Eff[_]: Functor, CallId, Res](
    request: CallId,
    commandMap: CallId => String
  )(implicit remote: Aux[Eff, Id, Const[Res, ?], Any, _]): Eff[Res] =
    remote.runGetCall((), request, commandMap).map(_.getConst)

  def runGet[Eff[_]: Functor, Res](
    command: String,
  )(implicit remote: Aux[Eff, Id, Const[Res, ?], Any, _]): Eff[Res] =
    remote.runGetCall((), (), (_: Any) => command).map(_.getConst)

  def runPost[Eff[_], CallId, Req[_], Res[_], CallConfig, Payload](
    callConfig: CallConfig,
    request: Req[CallId],
    commandMap: CallId => String,
    payload: Option[Payload] = None
  )(implicit remote: Aux[Eff, Req, Res, CallConfig, Payload]): Eff[Res[CallId]] =
    remote.runPostCall(callConfig, request, commandMap, payload)

  def runPost[Eff[_], CallId, Req[_], Res[_], Payload](
    request: Req[CallId],
    commandMap: CallId => String,
    payload: Option[Payload]
  )(implicit remote: Aux[Eff, Req, Res, Any, Payload]): Eff[Res[CallId]] =
    remote.runPostCall((), request, commandMap, payload)

  def runPost[Eff[_]: Functor, CallId, Res, Payload](
    request: CallId,
    commandMap: CallId => String,
    payload: Option[Payload]
  )(implicit remote: Aux[Eff, Id, Const[Res, ?], Any, Payload]): Eff[Res] =
    remote.runPostCall((), request, commandMap, payload).map(_.getConst)

  def runPost[Eff[_]: Functor, Res, Payload](
    command: String,
    payload: Option[Payload]
  )(implicit remote: Aux[Eff, Id, Const[Res, ?], Any, Payload]): Eff[Res] =
    remote.runPostCall((), (), (_: Any) => command, payload).map(_.getConst)

}
