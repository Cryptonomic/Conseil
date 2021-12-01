// package tech.cryptonomic.conseil

// import tech.cryptonomic.conseil.model.Info

// import cats.implicits._
// import cats.ApplicativeThrow

// object ServerSideExecutor {

//   def instance[F[_]: ApplicativeThrow]: Executor[F] =
//     new Executor[F] {
//       override def appInfo(os: Option[String]): F[Info.type] =
//         os.as(Info).liftTo[F](new Throwable("Unsupported!"))
//     }

// }
