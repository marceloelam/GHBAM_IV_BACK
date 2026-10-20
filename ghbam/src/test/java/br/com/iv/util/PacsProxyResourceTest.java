package br.com.iv.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertStore;
import java.security.cert.CertificateFactory;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXCertPathBuilderResult;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.junit.jupiter.api.Test;

class PacsProxyResourceTest {

    @Test
    void normalizaUrlAntigaDoRis() {
        assertEquals("https://ris.itaguai.rj.gov.br/api/laudopdf/1",
                PacsProxyResource.normalizarUrlPacs("http://ris.itaguai.rj.gov.br:9596/api/laudopdf/1"));
    }

    @Test
    void sslContextComIntermediariosInicializa() throws Exception {
        assertNotNull(PacsProxyResource.sslContextComIntermediarios());
    }

    // O RIS envia só a folha (emitida por YE1). Sem rede/AIA, YE1 precisa fechar numa raiz do JDK pelo PEM embutido.
    @Test
    void intermediarioYe1FechaCadeiaAteRaizDoJdk() throws Exception {
        List<X509Certificate> pem;
        try (InputStream in = getClass().getResourceAsStream("/certs/letsencrypt-intermediarios.pem")) {
            assertNotNull(in);
            pem = CertificateFactory.getInstance("X.509").generateCertificates(in).stream()
                    .map(X509Certificate.class::cast).toList();
        }
        X509Certificate ye1 = pem.stream()
                .filter(c -> c.getSubjectX500Principal().getName().contains("CN=YE1"))
                .findFirst().orElseThrow();

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init((KeyStore) null);
        Set<TrustAnchor> ancoras = new HashSet<>();
        for (TrustManager tm : tmf.getTrustManagers()) {
            if (tm instanceof X509TrustManager x509) {
                for (X509Certificate ca : x509.getAcceptedIssuers()) ancoras.add(new TrustAnchor(ca, null));
            }
        }

        X509CertSelector alvo = new X509CertSelector();
        alvo.setCertificate(ye1);
        PKIXBuilderParameters params = new PKIXBuilderParameters(ancoras, alvo);
        params.addCertStore(CertStore.getInstance("Collection", new CollectionCertStoreParameters(pem)));
        params.setRevocationEnabled(false);

        PKIXCertPathBuilderResult resultado =
                (PKIXCertPathBuilderResult) CertPathBuilder.getInstance("PKIX").build(params);
        assertTrue(resultado.getTrustAnchor().getTrustedCert().getSubjectX500Principal().getName().contains("ISRG Root X"));
    }
}
