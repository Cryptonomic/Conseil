Design principles of the proposal
=================================

The basic downsides we're tackling with this redesign are

- hard to log individual failures, hence hard to debug Lorre's issues
- the current traits for data fetching had become hard to understand as they try to handle too many different use-cases: single async requests, batched requests based on underlying akka-stream http, streaming fetches based on purely functional cats IO
- evolution of the system (e.g. addition of extra data like delegates or voting) is becoming unwieldy, as the batched/paginated nature of block's fething gets replicated to any kind of data fetching.

What this solution does to reduce or remove those downsides is
- provide a single generic api to make rpc calls to tezos (any chain node actually), exposing only a `get` and `post` functions
- define data-fetchers that doesn't model batching, but defines single-element fetching, easier to evolve and to instrument at the individual requestest level
- use IO to wrap multiple requests (lazily) and `fs2` library to create streams of requests that can be chunked more flexibly, to optimize data fetching (concurrent requests) and storage (batch inserts)

## The type classes approach
We use this pattern here as an alternative approach to dependency injection of functionality modules.

Essentially it works like this:

 - define a `trait` to expose some functionality, using type parameters to make it generic
 - define instances that implement said `trait` for different sets of type parameters
 - collect thise instances within scala `object` modules, to later import them into scope
 - define higher level services that expects to use such trait for a specific parametrization to compose more complex domain logic, and define the dependency on the `trait`/s as implicit parameters
 - the service can be itself generic on some type parameters (usually known as final tagless design), which means that the service method can have different results (e.g. wrapper effect type of the response: Future, IO, Try, Either, Option,...) based on the implicit dependencies available at _call-site_

Let's write a contrived and quite artificial example to give a real meaning to this description

```scala
import cats._
import cats.implicits._

/* defines a logging capability, generic in the effect of the result, and the input expected */
trait Logger[Eff[_], Msg] {
  def info(message: Msg): Eff[String]
}

/* provides one instance for a specific parametric types set, i.e. Future and String */
object AsyncLoggers {

  implicit val futureLoggerInstance = new Logger[Future, String] {
    override def info(message: String): Future[String] = ???
  }
}

/* define a service that needs the logging as a functional dependency,
 * and provide it as an implicit argument
 */
class CustomService {

  def serviceCallNeedingLogging[Eff[_] : FlatMap](
    input: CustomData
  )(implicit
    log: Logger[Eff, String]
  ): Eff[(ReturnType, String)] = {

      val computation: Eff[ReturnType] = externalCall[Eff](input)

      /* here we use the flatMap capability, implicitly available
       * once we defined `Eff[_] : FlatMap` as a "type bound for Eff".
       * It is essentially converted under-the-hood to an extra implicit FlatMap[Eff]
       * parameter
       */
      for {
        computedValue <- computation
        logginOutcome <- log.info(computedValue)
      } yield (computedValue, logginOutcome)

  }
}

/* use the service with a specific instance of the Logging capability */
def main() {

  //create the service
  val service = new CustomService

  //make the logging instance implictly available as a dependency
  import AsyncLoggers._

  //add extra imports to provide extra type classes for Future: i.e. FlatMap
  import cats.....

  //call the service using the instance, implicitly
  service.serviceCallNeedingLogging[Future](input): Future[(ReturnType, String)]
}
```

The design allows to switch to a different implementation of the `Logger` capability at calls-site (using `cats.effect.IO` for example), within the `main` code, simply by importing a different module object instead of `AsyncLoggers`, or adding more instances within that same "module".
[Technically this is true only as long as the `service` method use a commpatible `Eff` to flatMap, but that's an over-simplification of the example]

## Actual type classes for data-fetching and Tezos Node Operator refactor

Where we move on to describe the actual main components used and how they are connected and used in the approach just described

### RPCHandlers

This generic abstract trait defines how to make an RPC call to a tezos node

```scala
trait RpcHandler[Eff[_], Command, Response] {
  import cats.data.Kleisli

  /** the type of a Post payload, only used for Post calls */
  type PostPayload

  /** describes the effectful execution of a GET http call */
  def getQuery: Kleisli[Eff, Command, Response]

  /** describes the effectful execution of a POST http call */
  def postQuery: Kleisli[Eff, (Command, Option[PostPayload]), Response]

}
```

It's parametric on
- `Eff` the output wrapper used to describe effectful responses (e.g. IO, Future, Try, ...)
- `Command` the input type, used to identify the tezos endpoint when building the request URL (generally the String representing the subpath for the specific entity)
- `Response` the output type, representing what the node replied (simplest is a String representing the json body)
- `PostPayload` internal type (not visible in the trait definition), used to define how we represent a POST payload (e.g. a String representing json)

The methods exposed actually return a description of an effectful function `Command => Eff[Response]`, encoded in the `Kleisli` type.
Execution of the call is done by calling `apply`, `.run(command)`, or simply using the Kleisli as a function object, as in

`handler.getQuery(command)` or `handler.getQuery.apply(command)` or `handler.getQuery.run(command)`

The companion object provides utility methods to "invoke" and instance or make the calls, whenever an appropriate rpc-handler is available in the implicit scope.

```scala
object RpcHandler {

  def apply[Eff[_], Command, Response,  PostPayload](
    implicit rpc: Aux[Eff, Command, Response, PostPayload]
  ): RpcHandler[Eff, Command, Response]

  def runGet[Eff[_], Command, Response](
    command: Command
  )(implicit
    rpc: RpcHandler[Eff, Command, Response]
  ): Eff[Response]

  def runPost[Eff[_], Command, Response, PostPayload](
    command: Command,
    payload: Option[PostPayload] = None
  )(implicit
    rpc: Aux[Eff, Command, Response, PostPayload]
  ): Eff[Response]

}
```

As you might notice, there's a misterious `Aux` type as an implicit. It's a fairly simple type alias that allows to "make visible" the `PostPayload` internal type in type signatures (needed to drive the implicit resolution)

`type Aux[Eff[_], Command, Response, Payload] = RpcHandler[Eff, Command, Response] { type PostPayload = Payload }`

#### side note
I personally chose to put some of the types internally in a class or trait to simplify the type definition in general, yet I'm not expert enough (didn't study the topic enough yet) to know the advanced tricks to be able to drive the resolution using only the minimal information needed at call site.
The idea would be to expose the bare minimum types to the calling code, and use type inference to "fix" the rest.
At this moment the Aux type is still explicitly used to help the inferencer wherever needed

Using the utility methods, the call is executed like
```scala
//assuming a rpc handler in scope implicitly

val headBlockCommand = "blocks/head"

RpcHandler.runGet(headBlockCommand) // assuming IO effect and String response, this returns IO[String]
```

or by specifying the types if, for example, more than one instance is available and we need to choose which one to use
```scala
//assuming more rpc handlers in scope implicitly

val headBlockCommand = "blocks/head"

RpcHandler.runGet[IO, String, JsonString](headBlockCommand) //returns IO[JsonString]
```

Additionally, thanks to Kleisli's rich combinators' api, it's quite easy to define an handler for the most simple types and then define transformations and input adapters after the fact, to derive more complex ones. To give an idea:
```scala
val simpleGet: Kleisli[IO, String, String] = RpcHandler[IO, String, String]

val jsonGet: Kleisli[IO, String, Json] = simpleGet.map(toJson) //assuming some toJson method from a library

val urlGet: Kleisli[IO, URL, String] = simpleGet.local(url => url.getPath.removeHost.toString) //adapts the input before making the call

val urlJsonGet: Kleisli[IO, URL, Json] = simpleGet.dimap(url.getPath.removeHost.toString)(toJson) //does both in one shot
```

As a last useful trick, we defined what's called a `FunctionK`, which is a definition from _cats_ of a function operating on type constructors (or type wrappers), to convert between "containers/contexts" without caring about the actual type contained therein.

This gives us the power to convert an implementation of rpc handler using one effect (e.g. Future) to another effect (e.g. IO) just by providing a generic way to convert the "wrappers".

In practice, if we have a  `FunctionK[Future, IO]` implicitly available (a.k.a. natural transformation `Future ~> IO`), we can do
```scala
//assuming we have Future ~> IO in scope

val futureBasedHandler: RpcHandler[Future, String, String] = ???

val ioBasedHandler: RpcHandler[IO, String, String] = RpcHandler.functionK(futureBasedHandler)
```

### DataFetchers

This generic abstract trait defines how to read data from a tezos node and convert the json to an expected scala type.

```scala
trait DataFetcher[Eff[_], Err] {
  type In
  type Out
  type Encoded

  def fetchData: Kleisli[Eff, In, Encoded]

  def decodeData: Kleisli[Eff, Encoded, Out]

  def onDataFetchError: Err => Unit = Function.const(())

  def onDecodingError: Err => Unit = Function.const(())

}
```
It's parametric on
- `Eff` the output wrapper used to describe effectful responses (e.g. IO, Future, Try, ...)
- `Err` the type of a possible error, used to be match upon and handle operation failures with custom handling
- `In` internal input type (not visible in the trait definition), used to identify the tezos endpoint when making a request, e.g. the offset used to identify a specific block relative to the head, or an account id for a block level
- `Out` internal output type (not visible in the trait definition), representing the final object returned by the complete fetch operation, as decoded from the remote call format
- `Encoded` internal type (not visible in the trait definition), used to define how the response from a remote call is actually encoded. Almost always a `String` containing some json payload.

The exposed methods describes
- the 2 phases of fetching a remote object: get the data from tezos as an encoded content, decode the content
- 2 custom handlers (used for side-effecting) specific to each phase, in case the call should fail. The most obvious thing to do is to log the error. By default nothing is done.

#### regarding the error-handlers

The provided error handlers are quite limited in their reporting capabilities, as they cannot inspect the input values of the failed operation. We might consider to totally remove them if they're found to be essentially useless.
The implemented fetchers handle the failures already in the `fetch/decode` methods, to provide more useful information.

Same as for the RpcHandler, the companion object provides the necessary functions and types to execute composite operations, when the appropriate fetcher is defined in the implicit scope

```scala
object DataFetcher {

  def fetcher[Eff[_], In, Out, Err](implicit
    appErr: ApplicativeError[Eff, Err],
    flatMap: FlatMap[Eff],
    fetcher: DataFetcher.Aux[Eff, Err, In, Out, _]
  ): Kleisli[Eff, In, Out] = ...

  type Aux[Eff[_], Err, Input, Output, Encoding] = ...

  def multiDecodeFetcher[Eff[_]: Apply, Err, Input, Output, Output2, Encoding](
    implicit
    fetcher: Aux[Eff, Err, Input, Output, Encoding],
    additionalDecode: Kleisli[Eff, Encoding, Output2]
  ): Aux[Eff, Err, Input, (Output, Output2), Encoding] = ...


  def functionK[F[_], G[_], Err, Input, Encoding](
    implicit nat: F ~> G
  ): FunctionK[Aux[F, Err, Input, ?, Encoding], Aux[G, Err, Input, ?, Encoding]] = ...

}
```

*Aux*

Here we see the usual type alias needed to "expose" the internal types in signatures for implicit requirements, using the "Aux pattern"

`type Aux[Eff[_], Err, Input, Output, Encoding]`

essentially outlines all the types for a specific instance of data-fetcher.

*fetcher*

The main function available is `fetcher`: it makes use of an implicit DataFetcher of requested effect, error, input, output, enconding types.
The returned value is a Kleisli function that converts `In => Eff[Out]`, where the `Eff` effect wrapper can actually fail with error `Err`.

Additional requirements for `Eff` are exposed.

An example of usage is how to load accounts

```scala
  def getAccountsForBlock[Eff[_] : ApplicativeThrow : Concurrent](
    accountIds: Stream[F, AccountId]
  )(
    implicit fetcherForBlock: DataFetcher.Aux[Eff, Throwable, AccountId, Option[Account], String]
  ): Stream[F, (AccountId, Account)] = {

    accountIds.parEvalMap(batchConf.accountFetchConcurrencyLevel)(
      fetcher.tapWith((_, _)).run // tapWith returns both the input and the output, combined with the passed-in function
    )
    .collect {
      case (accountId, Some(account)) => accountId -> account
    }

  ```

Here we have a `Stream` of ids and an implicit fetcher from `AccountId` to `Option[Account]`, wrapped in the `Eff`.
We use the `fetcher` method to get a reference to the Kleisli function, that we `tapWith` to tuple both `AccountId` and corresponding `Account` on the output.
Finally we run the function for each id in the stream, and collect only those found (keeping only when the option is `Some`).

Having the `fetcher` kleisli, as seen here, provides many possible combinators/operators to act on the actual effectful function, before running it. E.g. in the code we use multiple fetchers that take the same head hash and offset to provide a tuple of different voting data, making use of the fact that a kleisli fetcher has Applicative semantic to make parallel calls with effects.

*multiDecodFetcher*

We provide this combinator on an existing fetcher, to actually fetch the same json and allow us to "compose" a second decoder that will return a tuple of both decoded values.

This turns useful to read the same json from the `/operations` endpoint and extract both operation groups and account ids. The input is implicitly passed, so having the appropriate fetcher and decoder in scope allow us to write
```scala
val oeprationsWithAccountsFetcher = DataFetcher.multiDecodeFetcher[IO, Throwable, BlockHash, List[OperationsGroup], List[AccountId], String]
```

The type inference will do the rest by itself, looking for the appropriate input types in scope.

*functionK*

In the same way we did with rpc-handlers, here too there's a `functionK` provided, to derive a data-fetcher for a different effect, based on an existing one, as long as a corresponding `functionK` (a.k.a. natural transformation `F ~> G`) exists between the effects.

Once again the example is that if we have an implicit `cats.effect.IO ~> zio.Task` we can create a DataFetcher with effect `IO` and turn it into one for `Task`.

*profunctor*

Finally, in the `DataFetcher.Instances` submodule (a scala `object`) we define an implicit instance of

`Profunctor[Aux[Eff, Err, ?, ?, Encoding]]`

This strangely named type class provides the same useful combinators on input and outputs as shown for kleisli, in the rpc-handler examples.

How do we make use of this? Let's make an example with the operation groups in a block json.

```scala
//makes the required implicit visible
import DataFetcher.Instances._

//used to get a type conforming to the expected type parameters numbers for profunctors (in & out)
type IOFetcher[In, Out] = Aux[IO, Throwable, In, Out, String]

//we start with a simple definition of a DataFetcher that takes a string (url fragment), and returns a decoded List[List[OperationGroup]]
val rawFetcher: IOFetcher[String, List[List[OperationGroup]]] = ...

//we can the use dimap to both adapt input to a block hash reference, and output to a flattened List

//this creates the path from the hash
val makeCommand = (hash: BlockHash) => s"blocks/${hash.value}/operations"

val operationGroupsFetcher: IOFetcher[BlockHash, List[OperationGroup]] = Profuctor[IOFetcher].dimap(rawFetcher)(makeCommand)(_.flatten)
```

In the same vein we can only modify one side of the operation, hence we have a `lmap` (to "left" map on input) and `rmap` (to "right" map on output).

### Putting everything together

#### TezosRemoteInstances
The rpc-handler for tezos is built re-using the legacy core, which calls the akka-http client, using the proper configuration, and returns a `Future` response.

Configuration is passed-in as an implicit dependency of type `TezosNodeContext`, which contains all needed contact point definitions based on the reference.conf.

Having this object implicitly provides an instance by `import TezosRemoteInstances.Akka.Futures._`.

We make a cats effect rpc-handler using the available akka instance, thus the actual usage is `import TezosRemoteInstances.Cats.IOEff._`

The handler is thus a `RpcHandler.Aux[IO, String, String, JsonString]`

#### TezosNodeFetchers
The data-fetchers are defined essentially using the same pattern in the `BlockDataFetchers` (for block-related data) and `AccountsDataFetchers` (for accounts/delegates) objects.

The fetch part is using an internal rpc-handler to make the remote calls, and eventually adapt/convert input/output, based on the type we need.

#### NodeOperator

The operator methods are now generic in the effect type `F`, and each method will have its own requirement in the signatures.

This implies that technically we can build a single NodeOperator instance and use it with different data-fetchers instances in scope, to provide a different effect implementation. Right now we're using `cats.effect.IO` as the effect type, but we could easily swap it if needed.

We could go as far as provide locally-scoped instances and make calls on the NodeOperator that return Futures or else, as long as the signature type class bounds are fullfilled.

Each method requires such bound as are necessary in the operations performed in the method (e.g. `Functor` to use `map`, `ApplicativeError` to use `onError`, `Monad` to use `flatMap` and so on).

#### Streaming Lorre

To read multiple offsets efficiently, the re-designed version of Lorre uses `IO` for "lazy async" effects, and computes the required offsets as an effectful `Stream` from the `fs2` library.

This means that the offsets are lazily transformed and passed to the node-operator calls to load a stream of blocks data. Such stream, still lazy, is then `mapped/flatMapped` as needed to read the derivative data, like voting and accounts to store.

The result of "running" the stream is then combined (within the IO effect still) with db write operations, wrapped themselves into IO values.

The combined IO value is not executed until the end of the cycle, it's just a "description" of the side-effecting operations to execute.

We then compose those sequentially with similar IO and fs2.Stream combinations to read back the account checkpoint and fetch accounts, which are in turn written, adding delegates references to another checkpoint table.

The final step use once more a similar definition to read delegate hashes, fetch them in streaming and store them on db.

Finally the combined IO program is eventually run, executing the effects and possibly failing.

To make the app definition consistent, most effectful operations (like logging, thread sleeps, reading the system clock) are wrapped into IO, and composed with the rest using IO combinators (`flatMap`, `*>`, `<*`, ...).

The streaming elements (be them blocks, accounts, ...) are "chunked" to be processed in small batches, the size of which is defined via configuration. This enables database batching operations to be actually used. At the same time, the asynchronous rpc calls are done concurrently, so that each element will not actually block the wait for the previous during this operation, but will be fetched together, up to a configured concurrency level.

The resulting code is currently contained in a massive method called `processBlocks(...)`, which defines several internal methods to decompose the process, and then puts it all together.

This means that Lorre code is not well-organized at the moment, yet the PR will be proposed as-is to release the rather sooner than later, with the propotion to refactor it later, in smaller steps, in whatever manner is deemed most valuable.