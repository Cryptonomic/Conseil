package tech.cryptonomic.conseil.api.util

/* The following code is adapted from https://github.com/seahrh/concurrent-scala and subject to licensing terms hereby specified
 * MIT License
 *
 * Copyright (c) 2017 Ruhong
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
import tech.cryptonomic.conseil.api.util.Retry._
import org.scalatest.FlatSpec
import org.scalatest.concurrent.ScalaFutures
import org.scalamock.scalatest.MockFactory
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{fromNow, Deadline, DurationLong}
import scala.concurrent.Future
import org.scalatest.time.{Seconds, Span}
import org.scalatest.Matchers
import org.scalamock.function.StubFunction1

class RetryTest extends FlatSpec with Matchers with MockFactory with ScalaFutures {

  implicit override val patienceConfig = PatienceConfig(timeout = Span(4, Seconds))
  private val waitAtMost: Span = patienceConfig.timeout

  class Bar {
    def inc(i: Int): Int = i + 1

    def incWithDelay(i: Int): Int = {
      println("delay")
      Thread.sleep(100)
      inc(i)
    }
  }

  private def giveUpOnIllegalArgumentException(t: Throwable): Boolean = t match {
    case _: IllegalArgumentException => true
    case _ => false
  }

  "Future" should "return value if block succeeds without retry" in {
      val bar = stub[Bar]
      val maxRetry = Some(1)
      val foo: StubFunction1[Int, Int] = bar.inc _

      (foo).when(1).returns(2).once

      val f: Future[Int] = retry[Int](maxRetry)(foo(1))
      assertResult(2)(f.futureValue)

    }

  it should "return value if block succeeds with retry" in {
      val bar = stub[Bar]
      val maxRetry = Some(1)
      val foo: StubFunction1[Int, Int] = bar.inc _

      inSequence(
        Seq(
          (foo).when(1).throws(new RuntimeException).once,
          (foo).when(1).returns(2).once
        )
      )

      val f: Future[Int] = retry[Int](maxRetry)(foo(1))
      assertResult(2)(f.futureValue)

    }

  it should "throw TooManyRetriesException (with cause) if block fails and no retry is allowed" in {
      val bar = stub[Bar]
      val maxRetry = Some(0)
      val foo: StubFunction1[Int, Int] = bar.inc _

      (foo).when(1).throws(new IllegalStateException).once

      val f: Future[Int] = retry[Int](maxRetry)(foo(1))

      val caught = f.failed.futureValue
      caught shouldBe a[TooManyRetriesException]
      caught.getCause shouldBe a[IllegalStateException]

    }

  it should "throw TooManyRetriesException (with cause) if all retries are used up" in {
      val bar = stub[Bar]
      val maxRetry = Some(1)
      val foo: StubFunction1[Int, Int] = bar.inc _

      (foo).when(1).throws(new IllegalStateException)

      val f: Future[Int] = retry[Int](maxRetry)(foo(1))

      val caught = f.failed.futureValue
      caught shouldBe a[TooManyRetriesException]
      caught.getCause shouldBe a[IllegalStateException]
    }

  "Number of Attempts" should "execute block exactly once if first attempt passes" in {
      val bar = stub[Bar]
      val maxRetry = Some(1)
      val foo: StubFunction1[Int, Int] = bar.inc _

      (foo).when(1).returns(2)

      val f: Future[Int] = retry[Int](maxRetry)(foo(1))

      // Do not use callback as it is non-blocking,
      // any exception thrown results in test pass.
      // Instead, block till future completes.
      f.isReadyWithin(waitAtMost)

      (foo).verify(1).once

    }

  it should "execute block 2 times if first attempt fails" in {
      val bar = stub[Bar]
      val maxRetry = Some(1)
      val foo: StubFunction1[Int, Int] = bar.inc _

      inSequence(
        Seq(
          (foo).when(1).throws(new RuntimeException).once,
          (foo).when(1).returns(2).once
        )
      )

      val f: Future[Int] = retry[Int](maxRetry)(foo(1))
      f.isReadyWithin(waitAtMost)

      (foo).verify(1).repeated(maxRetry.get + 1)
    }

  it should "execute block 3 times if earlier attempts fail" in {
      val bar = stub[Bar]
      val maxRetry = Some(2)
      val foo: StubFunction1[Int, Int] = bar.inc _

      inSequence(
        Seq(
          (foo).when(1).throws(new RuntimeException).twice,
          (foo).when(1).returns(2).once
        )
      )

      val f: Future[Int] = retry[Int](maxRetry)(foo(1))

      f.isReadyWithin(waitAtMost)
      (foo).verify(1).repeated(maxRetry.get + 1)
    }

  it should "execute block exactly once if max #retries is zero" in {
      val bar = stub[Bar]
      val maxRetry = Some(0)
      val foo: StubFunction1[Int, Int] = bar.inc _

      inSequence(
        Seq(
          (foo).when(1).throws(new RuntimeException),
          (foo).when(1).returns(2)
        )
      )

      val f: Future[Int] = retry[Int](maxRetry)(foo(1))

      f.failed.isReadyWithin(waitAtMost)
      (foo).verify(1).noMoreThanOnce

    }
  it should "be unlimited if max #retries is none" in {
      val bar = stub[Bar]
      val maxRetry = None
      val foo: StubFunction1[Int, Int] = bar.inc _

      inSequence(
        Seq(
          (foo).when(1).throws(new RuntimeException).repeated(3),
          (foo).when(1).returns(2)
        )
      )

      val f: Future[Int] = retry[Int](maxRetry)(foo(1))

      f.isReadyWithin(waitAtMost)
      (foo).verify(1).repeated(4)

    }

  "Deadline" should "not be exceeded by retries" in {
      val bar = stub[Bar]
      val maxRetry = None
      val foo: StubFunction1[Int, Int] = bar.incWithDelay _

      def deadline: Option[Deadline] = Option(400 millisecond fromNow)

      (foo).when(1).throws(new RuntimeException)

      val f: Future[Int] = retry[Int](maxRetry, deadline, noBackoff())(foo(1))

      f.failed.isReadyWithin(waitAtMost)

      (foo).verify(1).repeated(3 to 4).times

    }
  it should "throw DeadlineExceededException (with cause) if exceeded" in {
      val bar = stub[Bar]
      val maxRetry = None
      val foo: StubFunction1[Int, Int] = bar.inc _

      def deadline: Option[Deadline] = Option(50 millisecond fromNow)

      (foo).when(1).throws(new IllegalStateException)

      val f: Future[Int] = retry[Int](maxRetry, deadline)(foo(1))

      val caught = f.failed.futureValue

      caught shouldBe a[DeadlineExceededException]
      caught.getCause shouldBe a[IllegalStateException]
    }

  "Give Up On Selected Exceptions" should "not retry if selected exception is seen in 1st attempt" in {
      val bar = stub[Bar]
      val maxRetry = Some(10)
      val foo: StubFunction1[Int, Int] = bar.inc _

      (foo).when(1).throws(new IllegalArgumentException)

      val f: Future[Int] = retry[Int](
        maxRetry,
        giveUpOnThrowable = giveUpOnIllegalArgumentException
      )(foo(1))

      f.failed.isReadyWithin(waitAtMost)
      (foo).verify(1).once
    }

  it should "retry until selected exception is seen" in {
      val bar = stub[Bar]
      val maxRetry = Some(10)
      val foo: StubFunction1[Int, Int] = bar.inc _

      inSequence(
        Seq(
          (foo).when(1).throws(new IllegalStateException).once,
          (foo).when(1).throws(new IllegalArgumentException).once
        )
      )

      val f: Future[Int] = retry[Int](
        maxRetry,
        giveUpOnThrowable = giveUpOnIllegalArgumentException
      )(foo(1))

      f.failed.isReadyWithin(waitAtMost)

      (foo).verify(1).twice
    }

  it should "retry normally if selected exception is never seen" in {
      val bar = stub[Bar]
      val maxRetry = Some(2)
      val foo: StubFunction1[Int, Int] = bar.inc _

      (foo).when(1).throws(new IllegalStateException)

      val f: Future[Int] = retry[Int](
        maxRetry,
        giveUpOnThrowable = giveUpOnIllegalArgumentException
      )(foo(1))

      f.failed.isReadyWithin(waitAtMost)
      (foo).verify(1).repeated(3)
    }
}
