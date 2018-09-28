package tech.cryptonomic.conseil.util

import scala.util.{Success, Try}
import scala.concurrent.{Await, ExecutionContext, Future}

object FPUtil {

  /*
  def sequence[A](a: List[Future[A]]): Future[List[A]] = {
    a.foldRight[Future[List[A]]](Future((x,y) => x.flatMap( z => y.map(w => z :: w)))
  }*/

//  def sequence[A](lma: List[F[A]]): F[List[A]] = F(lma.flatten)


}
