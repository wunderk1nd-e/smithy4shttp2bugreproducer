import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.io.file.Files
import fs2.io.net.tls.TLSContext
import org.http4s.client.Client
import org.http4s.client.middleware.{RequestLogger, ResponseLogger}
import org.http4s.headers.`User-Agent`

import java.nio.file.Paths
import javax.net.ssl.SSLContext
object TlsContext {
  val keystoreResourcePath = "teststore.jks"
  val keystorePassword = "testtest"

  // Create the SSLContext
  val sslContext = SSLContext.getDefault

  // Create the TLSContext
  val tlsContext = TLSContext.Builder
    .forAsync[IO]
    .fromKeyStoreResource(
      keystoreResourcePath,
      keystorePassword.toCharArray,
      keystorePassword.toCharArray
    )
    .unsafeRunSync()

}
