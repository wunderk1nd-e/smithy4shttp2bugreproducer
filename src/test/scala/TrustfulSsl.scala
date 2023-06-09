import akka.http.scaladsl.{ConnectionContext, HttpsConnectionContext}

import java.security.cert.X509Certificate
import javax.net.ssl.{KeyManager, SSLContext, X509TrustManager}

object TrustfulSSL {

  val trustfulSslContext: HttpsConnectionContext = {

    object NoCheckX509TrustManager extends X509TrustManager {
      override def checkClientTrusted(chain: Array[X509Certificate], authType: String) = ()

      override def checkServerTrusted(chain: Array[X509Certificate], authType: String) = ()

      override def getAcceptedIssuers = Array[X509Certificate]()
    }

    val context = SSLContext.getInstance("TLS")
    context.init(Array[KeyManager](), Array(NoCheckX509TrustManager), null)
    ConnectionContext.https(context)
  }

}
