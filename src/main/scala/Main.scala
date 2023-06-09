import smithy4s.hello._
import cats.effect._
import cats.implicits._
import org.http4s.implicits._
import org.http4s.ember.server._
import org.http4s._
import com.comcast.ip4s._
import smithy4s.http4s.SimpleRestJsonBuilder
import org.http4s.dsl.io._
import org.http4s.server.Server

object HelloWorldImpl extends HelloWorldService[IO] {

  override def helloUnknown(): IO[Greeting] = IO.pure(Greeting("Hello unknown friend!"))
  def hello(name: String, town: Option[String]): IO[Greeting] = IO.pure {
    town match {
      case None    => Greeting(s"Hello $name!")
      case Some(t) => Greeting(s"Hello $name from $t!")
    }
  }
}

object Routes {
  private val example: Resource[IO, HttpRoutes[IO]] =
    SimpleRestJsonBuilder.routes(HelloWorldImpl).resource

  private val docs: HttpRoutes[IO] =
    smithy4s.http4s.swagger.docs[IO](HelloWorldService)

  private val simple: HttpRoutes[IO] = HttpRoutes.of[IO] { case GET -> Root / "vanilla" =>
    Ok("ice cream!")
  }

  val all: Resource[IO, HttpRoutes[IO]] = example.map(_ <+> docs <+> simple)
}

object Servers {

  def createServer(
      port: Int,
      f: EmberServerBuilder[IO] => EmberServerBuilder[IO]
  ): Resource[IO, Server] = Routes.all.flatMap(routes =>
    f(
      EmberServerBuilder
        .default[IO]
        .withPort(Port.fromInt(port).get)
        .withHost(host"0.0.0.0")
        .withHttpApp(routes.orNotFound)
    ).build
  )

}
