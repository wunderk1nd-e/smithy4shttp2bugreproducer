import org.scalatest._
import flatspec._
import matchers._
import Servers.createServer
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source}
import cats.effect.{IO, Resource}
import cats.effect.unsafe.implicits.{global => catsGlobal}
import cats.syntax.all._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{Await, Future, Promise}
import scala.util.Random
import TrustfulSSL.trustfulSslContext

class Reproduction extends AnyFlatSpec with should.Matchers with BeforeAndAfterAll {
  implicit val system: ActorSystem = ActorSystem("TestSystem")

  var cancelToken: IO[Unit] = IO.unit

  val httpPlaintextPort = 8000 + Random.nextInt(1000)
  val httpsPort = 8000 + Random.nextInt(1000)
  val httpTwoPlaintextPort = 8000 + Random.nextInt(1000)
  val httpsTwoPort = 8000 + Random.nextInt(1000)

  private val awaitTimeout: FiniteDuration = 1.seconds

  override def beforeAll(): Unit = {
    super.beforeAll()
    cancelToken = (
      Resource.eval(IO.println("Starting servers")),
      createServer(httpPlaintextPort, identity),
      createServer(httpsPort, _.withTLS(TlsContext.tlsContext)),
      createServer(httpTwoPlaintextPort, _.withHttp2),
      createServer(httpsTwoPort, _.withTLS(TlsContext.tlsContext).withHttp2)
    ).tupled.allocated
      .flatTap(_ => IO.println("Servers started!"))
      .unsafeRunSync()
      ._2
  }

  override def afterAll(): Unit = {
    Await.result(system.terminate(), awaitTimeout)
    val stop = IO.println("Servers stopping...") >> cancelToken >> IO.println("Servers stopped!")
    stop.unsafeRunSync()
    super.afterAll()
  }

  "http1" should "work for vanilla endpoint" in {
    val port = httpPlaintextPort
    val request = HttpRequest(uri = s"http://127.0.0.1:$port/vanilla")

    val result = Await.result(Http().singleRequest(request), awaitTimeout)

    assert(result.status.isSuccess())
  }

  it should "work for a 404" in {
    val port = httpPlaintextPort
    val request = HttpRequest(uri = s"http://127.0.0.1:$port/thisroutedoesntexist")

    val result = Await.result(Http().singleRequest(request), awaitTimeout)

    result.status.intValue() should equal(404)
  }

  it should "work for the smithy endpoint" in {
    val port = httpPlaintextPort
    val request = HttpRequest(uri = s"http://127.0.0.1:$port/hello")

    val result = Await.result(Http().singleRequest(request), awaitTimeout)

    assert(result.status.isSuccess())
  }

  "http1 with TLS" should "work for vanilla endpoint" in {
    val port = httpsPort
    val request = HttpRequest(uri = s"https://127.0.0.1:$port/vanilla")

    val result =
      Await.result(Http().singleRequest(request, trustfulSslContext), awaitTimeout)

    assert(result.status.isSuccess())
  }

  it should "work for a 404" in {
    val port = httpsPort
    val request = HttpRequest(uri = s"https://127.0.0.1:$port/thisroutedoesntexist")

    val result =
      Await.result(Http().singleRequest(request, trustfulSslContext), awaitTimeout)

    result.status.intValue() should equal(404)
  }

  it should "work for the smithy endpoint" in {
    val port = httpsPort
    val request = HttpRequest(uri = s"https://127.0.0.1:$port/hello")

    val result =
      Await.result(Http().singleRequest(request, trustfulSslContext), awaitTimeout)

    assert(result.status.isSuccess())
  }

  "http2" should "work for vanilla endpoint" in {
    val port = httpTwoPlaintextPort
    val dispatch =
      singleRequest(Http().connectionTo("127.0.0.1").toPort(port).http2WithPriorKnowledge())

    val request = HttpRequest(uri = s"http://127.0.0.1:$port/vanilla")

    val result = Await.result(dispatch(request), awaitTimeout)

    assert(result.status.isSuccess())
  }

  it should "work for a 404" in {
    val port = httpTwoPlaintextPort
    val dispatch =
      singleRequest(Http().connectionTo("127.0.0.1").toPort(port).http2WithPriorKnowledge())

    val request = HttpRequest(uri = s"http://127.0.0.1:$port/thisroutedoesntexist")

    val result = Await.result(dispatch(request), awaitTimeout)

    result.status.intValue() should equal(404)
  }

  it should "work for the smithy endpoint" in {
    val port = httpTwoPlaintextPort

    val dispatch =
      singleRequest(Http().connectionTo("127.0.0.1").toPort(port).http2WithPriorKnowledge())

    val request = HttpRequest(uri = s"http://127.0.0.1:$port/hello")

    val result = Await.result(dispatch(request), awaitTimeout)

    assert(result.status.isSuccess())
  }

  "http2 with TLS" should "work for vanilla endpoint" in {
    val port = httpsTwoPort
    val request = HttpRequest(uri = s"https://127.0.0.1:$port/vanilla")

    val dispatch =
      singleRequest(
        Http()
          .connectionTo("127.0.0.1")
          .toPort(port)
          .withCustomHttpsConnectionContext(trustfulSslContext)
          .http2()
      )

    val result =
      Await.result(dispatch(request), awaitTimeout)

    assert(result.status.isSuccess())
  }

  it should "work for a 404" in {
    val port = httpsTwoPort
    val request = HttpRequest(uri = s"https://127.0.0.1:$port/thisroutedoesntexist")

    val dispatch =
      singleRequest(
        Http()
          .connectionTo("127.0.0.1")
          .toPort(port)
          .withCustomHttpsConnectionContext(trustfulSslContext)
          .http2()
      )

    val result =
      Await.result(dispatch(request), awaitTimeout)

    result.status.intValue() should equal(404)
  }

  it should "work for the smithy endpoint" in {
    val port = httpsTwoPort
    val request = HttpRequest(uri = s"https://127.0.0.1:$port/hello")

    val dispatch =
      singleRequest(
        Http()
          .connectionTo("127.0.0.1")
          .toPort(port)
          .withCustomHttpsConnectionContext(trustfulSslContext)
          .http2()
      )

    val result =
      Await.result(dispatch(request), awaitTimeout)

    assert(result.status.isSuccess())
  }

  // From https://doc.akka.io/docs/akka-http/current/client-side/http2.html
  def singleRequest(
      connection: Flow[HttpRequest, HttpResponse, Any],
      bufferSize: Int = 100
  ): HttpRequest => Future[HttpResponse] = {
    val queue =
      Source
        .queue(bufferSize, OverflowStrategy.dropNew)
        .via(connection)
        .to(Sink.foreach { response =>
          // complete the response promise with the response when it arrives
          val responseAssociation = response.attribute(ResponsePromise.Key).get
          responseAssociation.promise.trySuccess(response)
        })
        .run()

    req => {
      // create a promise of the response for each request and set it as an attribute on the request
      val p = Promise[HttpResponse]()
      queue
        .offer(req.addAttribute(ResponsePromise.Key, ResponsePromise(p)))
        // return the future response
        .flatMap(_ => p.future)
    }
  }
}
