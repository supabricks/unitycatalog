package io.unitycatalog.server.persist.dao;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Durable tombstone: a caller-assigned publication UUID can never be reused after deletion. */
@Entity
@Table(name = "sb_publication_identities")
public class PublicationIdentityDAO {
  @Id private UUID id;

  public PublicationIdentityDAO() {}

  public PublicationIdentityDAO(UUID id) {
    this.id = id;
  }
}
