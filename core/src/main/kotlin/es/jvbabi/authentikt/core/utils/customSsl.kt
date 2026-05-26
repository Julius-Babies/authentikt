package es.jvbabi.authentikt.core.utils

import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIOEngineConfig
import java.io.File
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

fun HttpClientConfig<CIOEngineConfig>.customSsl(certificateFiles: List<File>) {
    if (certificateFiles.isEmpty()) return
    engine {
        https {
            val certificateFactory = CertificateFactory.getInstance("X.509")

            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
            keyStore.load(null, null)
            certificateFiles.forEach { file ->
                val certificate = certificateFactory.generateCertificate(file.inputStream())
                keyStore.setCertificateEntry(file.absolutePath, certificate)
            }

            val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            trustManagerFactory.init(keyStore)

            trustManager = trustManagerFactory.trustManagers.first() as X509TrustManager
        }
    }
}