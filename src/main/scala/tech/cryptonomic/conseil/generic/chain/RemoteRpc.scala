package tech.cryptonomic.conseil.generic.chain

/** describes remote calls, adding extra things on top
  * `Eff` is the container effect for the call response
  * `Req` is a wrapper around the call input parameter, to allow variations from a single element call
  * `Res` is a response type that will include the call parameter too, to express a correlation from input to output
  *   when the call values are wrapped themselves
  */
trait RemoteRpc[Eff[_], Req[_], Res[_]] {

  /** defines the type of extra call parameters,
    *  sometimes needed to implement specific features without bolting
    * hard-coded configuration values
    */
  type CallConfig
  /** the type of a Post payload, when needed*/
  type PostPayload

  /** make a GET
    * @param callConfig extra call params needed by specific implementations (e.g. concurrency level), can be ignored if not needed
    * @param request a container `Req` of sort, for one or more input id (of type `CallId`), allow multiple requests for example
    * @param commandMap a function that, given the input `CallId` will generate the endpoint to call, essentially a url
    * @return a response embedded in a effect `Eff`, with a type that can include a relation to the input id `CallId`, e.g. a simple tuple `(CallId, ReturnValue)`
    * @tparam CallId the type of each specific endpoint call input, or correlation id for the call, it's highly dependent on each endpoint call type
    */
  def runGetCall[CallId](callConfig: CallConfig, request: Req[CallId], commandMap: CallId => String): Eff[Res[CallId]]

  /** make a POST
    * @param callConfig extra call params needed by specific implementations (e.g. concurrency level), can be ignored if not needed
    * @param request a container `Req` of sort, for one or more input id (of type `CallId`), allow multiple requests for example
    * @param commandMap a function that, given the input `CallId` will generate the endpoint to call, essentially a url
    * @param payload an optional post payload, of a type that the implementation can eventually handle
    * @return a response embedded in a effect `Eff`, with a type that can include a relation to the input id `CallId`, e.g. a simple tuple `(CallId, ReturnValue)`
    * @tparam CallId the type of each specific endpoint call input, or correlation id for the call, it's highly dependent on each endpoint call type
    */
    def runPostCall[CallId](callConfig: CallConfig, request: Req[CallId], commandMap: CallId => String, payload: Option[PostPayload] = None): Eff[Res[CallId]]

}

object RemoteRpc {
  import cats.{Id, Functor}
  import cats.data.Const
  import cats.syntax.functor._

  /* type aliases */

  /** The complete signature applies to
    * - wrapped input values
    * - an output which depends on the input value, for correlation
    * - a complex call configuration parameter, including extra information to execute the call
    * - the payload-encoding type for POST calls
    */
  type Aux[Eff[_], Req[_], Res[_], Conf, Payload] = RemoteRpc[Eff, Req, Res] { type CallConfig = Conf; type PostPayload = Payload }

  /** Support calls with no input wrapping (i.e. single values), and a simply-typed output, independent of the input value */
  type Basic[Eff[_], Result, Payload] = RemoteRpc.Aux[Eff, Id, Const[Result, ?], Any, Payload]

  /** Factory method based on an implicit instance available in scope */
  def apply[Eff[_], Req[_], Res[_], CallConfig, Payload](
    implicit remote: Aux[Eff, Req, Res, CallConfig, Payload]
  ): Aux[Eff, Req, Res, CallConfig, Payload] = remote

  /** A static call that uses the implicit `RemoteRpc` instance available for the expected paramter types */
  def runGet[Eff[_], CallId, Req[_], Res[_], CallConfig](
    callConfig: CallConfig,
    request: Req[CallId],
    commandMap: CallId => String
  )(implicit remote: Aux[Eff, Req, Res, CallConfig, _]): Eff[Res[CallId]] =
    remote.runGetCall(callConfig, request, commandMap)

  /** Simplified get call, doesn't require any extra call configuration parameter */
  def runGet[Eff[_], CallId, Req[_], Res[_]](
    request: Req[CallId],
    commandMap: CallId => String
  )(implicit remote: Aux[Eff, Req, Res, Any, _]): Eff[Res[CallId]] =
    remote.runGetCall((), request, commandMap)

  /** Simplified get call, doesn't require any extra call configuration parameter
    * and will make a single rpc call on the input `CallId` value
    */
  def runGet[Eff[_]: Functor, CallId, Res](
    request: CallId,
    commandMap: CallId => String
  )(implicit remote: Basic[Eff, Res, _]): Eff[Res] =
    remote.runGetCall((), request, commandMap).map(_.getConst)

  /** Simplified get call, doesn't require any extra call configuration parameter
    * and will make a single rpc call to the provided command url (or subpath, actually).
    * Since no input id is provided, the response cannot depend on it, hence the return type
    * will be a `cats.data.Const` of some type, ignoring the input.
    */
    def runGet[Eff[_]: Functor, Res](
    command: String,
  )(implicit remote: Basic[Eff,Res, _]): Eff[Res] =
    remote.runGetCall((), (), (_: Any) => command).map(_.getConst)

  /** A static call that uses the implicit `RemoteRpc` instance available for the expected paramter types */
  def runPost[Eff[_], CallId, Req[_], Res[_], CallConfig, Payload](
    callConfig: CallConfig,
    request: Req[CallId],
    commandMap: CallId => String,
    payload: Option[Payload] = None
  )(implicit remote: Aux[Eff, Req, Res, CallConfig, Payload]): Eff[Res[CallId]] =
    remote.runPostCall(callConfig, request, commandMap, payload)

  /** Simplified post call, doesn't require any extra call configuration parameter */
  def runPost[Eff[_], CallId, Req[_], Res[_], Payload](
    request: Req[CallId],
    commandMap: CallId => String,
    payload: Option[Payload]
  )(implicit remote: Aux[Eff, Req, Res, Any, Payload]): Eff[Res[CallId]] =
    remote.runPostCall((), request, commandMap, payload)

  /** Simplified post call, doesn't require any extra call configuration parameter
    * and will make a single rpc call on the input `CallId` value
    */
  def runPost[Eff[_]: Functor, CallId, Res, Payload](
    request: CallId,
    commandMap: CallId => String,
    payload: Option[Payload]
  )(implicit remote: Aux[Eff, Id, Const[Res, ?], Any, Payload]): Eff[Res] =
    remote.runPostCall((), request, commandMap, payload).map(_.getConst)

  /** Simplified post call, doesn't require any extra call configuration parameter
    * and will make a single rpc call to the provided command url (or subpath, actually).
    * Since no input id is provided, the response cannot depend on it, hence the return type
    * will be a `cats.data.Const` of some type, ignoring the input.
    */
  def runPost[Eff[_]: Functor, Res, Payload](
    command: String,
    payload: Option[Payload]
  )(implicit remote: Aux[Eff, Id, Const[Res, ?], Any, Payload]): Eff[Res] =
    remote.runPostCall((), (), (_: Any) => command, payload).map(_.getConst)

}
