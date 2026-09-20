package io.unitycatalog.server.utils;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.NetworkException;
import org.junit.jupiter.api.Test;

class JwksOperationsTest {
  @Test
  void keyProviderNetworkFailureRemainsAProviderFailure() {
    JwksOperations operations =
        new JwksOperations(null) {
          @Override
          public JwkProvider loadJwkProvider(String issuer) {
            return key -> {
              throw new NetworkException("provider unavailable", null);
            };
          }
        };
    assertThrows(
        NetworkException.class,
        () -> operations.verifierForIssuerAndKey("internal", "key", "RS512"));
  }
}
