package tech.cryptonomic.conseil.generic.chain

trait RemoteRpc[Eff[_], Req[_], Res[_]] {

  type CallConfig
  type PostPayload

  def runGetCall[CallId](callConfig: CallConfig, request: Req[CallId], commandMap: CallId => String): Eff[Res[CallId]]

  def runPostCall[CallId](callConfig: CallConfig, request: Req[CallId], commandMap: CallId => String, payload: Option[PostPayload]): Eff[Res[CallId]]

}

object RemoteRpc {

  type Aux[Eff[_], Req[_], Res[_], Conf, Payload] = RemoteRpc[Eff, Req, Res] { type CallConfig = Conf; type PostPayload = Payload }

  def apply[Eff[_], Req[_], Res[_], CC, PL](implicit remote: Aux[Eff, Req, Res, CC, PL]): Aux[Eff, Req, Res, CC, PL] = remote

  def runGet[Eff[_], Req[_], Res[_], CallConfig, CallId](
    callConfig: CallConfig, request: Req[CallId], commandMap: CallId => String
  )(
    implicit remote: Aux[Eff, Req, Res, CallConfig, _]
  ): Eff[Res[CallId]] = remote.runGetCall(callConfig, request, commandMap)

  def runPost[Eff[_], Req[_], Res[_], CallConfig, CallId, Payload](
    callConfig: CallConfig, request: Req[CallId], commandMap: CallId => String, payload: Option[Payload] = None
  )(
    implicit remote: Aux[Eff, Req, Res, CallConfig, Payload]
  ): Eff[Res[CallId]] = remote.runPostCall(callConfig, request, commandMap, payload)

}
