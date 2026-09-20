package io.unitycatalog.server.sdk.delta;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.common.RequestHeaders;
import com.linecorp.armeria.common.auth.AuthToken;
import io.unitycatalog.client.delta.api.DeltaTablesApi;
import io.unitycatalog.client.model.CreateCatalog;
import io.unitycatalog.client.model.CreateSchema;
import io.unitycatalog.server.base.BaseCRUDTest;
import io.unitycatalog.server.base.ServerConfig;
import io.unitycatalog.server.base.catalog.CatalogOperations;
import io.unitycatalog.server.sdk.catalog.SdkCatalogOperations;
import io.unitycatalog.server.sdk.schema.SdkSchemaOperations;
import io.unitycatalog.server.utils.TestUtils;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Raw-HTTP integration tests for the UC Delta API {@code POST /v1/.../tables} endpoint.
 *
 * <p>The auto-generated {@code unitycatalog-client} can't construct certain malformed payloads --
 * its DTOs reject them at compile time -- but a non-SDK client (Rust, Kernel) is not bound by the
 * SDK's typed shape. This test posts hand-crafted JSON straight at the endpoint to pin server- side
 * validation that the SDK tests cannot reach. Sister to {@link SdkCreateTableTest}, which covers
 * everything reachable through the typed client.
 */
public class PublicationIdentityTest extends BaseCRUDTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String CREATE_TABLE_PATH =
      "/api/2.1/unity-catalog/delta/v1/catalogs/"
          + TestUtils.CATALOG_NAME
          + "/schemas/"
          + TestUtils.SCHEMA_NAME
          + "/tables";

  private DeltaTablesApi deltaTablesApi;
  private WebClient client;

  @Override
  protected CatalogOperations createCatalogOperations(ServerConfig serverConfig) {
    return new SdkCatalogOperations(TestUtils.createApiClient(serverConfig));
  }

  @BeforeEach
  @Override
  @SneakyThrows
  public void setUp() {
    super.setUp();
    deltaTablesApi = new DeltaTablesApi(TestUtils.createApiClient(serverConfig));
    SdkSchemaOperations schemaOperations =
        new SdkSchemaOperations(TestUtils.createApiClient(serverConfig));
    catalogOperations.createCatalog(new CreateCatalog().name(TestUtils.CATALOG_NAME));
    schemaOperations.createSchema(
        new CreateSchema().name(TestUtils.SCHEMA_NAME).catalogName(TestUtils.CATALOG_NAME));
    client =
        WebClient.builder(serverConfig.getServerUrl())
            .auth(AuthToken.ofOAuth2(serverConfig.getAuthToken()))
            .build();
  }

  @Test
  public void publicationIdentitySurvivesLostReplyAndConditionalDeletion() throws Exception {
    String id = java.util.UUID.randomUUID().toString();
    String path = "/api/2.1/unity-catalog/tables";
    String full =
        path + "/" + TestUtils.CATALOG_NAME + "." + TestUtils.SCHEMA_NAME + ".publication";
    String location =
        java.nio.file.Files.createTempDirectory(testDirectoryRoot, "published").toUri().toString();
    String body =
        MAPPER.writeValueAsString(
            java.util.Map.of(
                "name",
                "publication",
                "catalog_name",
                TestUtils.CATALOG_NAME,
                "schema_name",
                TestUtils.SCHEMA_NAME,
                "table_type",
                "EXTERNAL",
                "data_source_format",
                "DELTA",
                "storage_location",
                location,
                "columns",
                java.util.List.of()));
    var create =
        RequestHeaders.builder(HttpMethod.POST, path)
            .contentType(MediaType.JSON)
            .set("X-Supabricks-Table-Id", id)
            .build();
    var response = client.execute(create, HttpData.ofUtf8(body)).aggregate().join();
    assertThat(response.status().code()).isEqualTo(200);
    assertThat(MAPPER.readTree(response.contentUtf8()).get("table_id").asText()).isEqualTo(id);
    assertThat(client.execute(create, HttpData.ofUtf8(body)).aggregate().join().status().code())
        .isEqualTo(400);
    assertThat(
            MAPPER
                .readTree(client.get(full).aggregate().join().contentUtf8())
                .get("table_id")
                .asText())
        .isEqualTo(id);
    var wrong =
        RequestHeaders.builder(HttpMethod.DELETE, full)
            .set("X-Supabricks-Table-Id", java.util.UUID.randomUUID().toString())
            .build();
    assertThat(client.execute(wrong).aggregate().join().status().code()).isEqualTo(400);
    assertThat(client.get(full).aggregate().join().status().code()).isEqualTo(200);
    var remove =
        RequestHeaders.builder(HttpMethod.DELETE, full).set("X-Supabricks-Table-Id", id).build();
    assertThat(client.execute(remove).aggregate().join().status().code()).isEqualTo(200);
    assertThat(java.nio.file.Files.exists(java.nio.file.Path.of(java.net.URI.create(location))))
        .isTrue();
    assertThat(client.execute(create, HttpData.ofUtf8(body)).aggregate().join().status().code())
        .isEqualTo(400);
    // A recreation at the old alias has a different identity; an old cleanup cannot remove it.
    var plain = RequestHeaders.builder(HttpMethod.POST, path).contentType(MediaType.JSON).build();
    assertThat(client.execute(plain, HttpData.ofUtf8(body)).aggregate().join().status().code())
        .isEqualTo(200);
    assertThat(client.execute(remove).aggregate().join().status().code()).isEqualTo(400);
    assertThat(client.get(full).aggregate().join().status().code()).isEqualTo(200);
  }
}
