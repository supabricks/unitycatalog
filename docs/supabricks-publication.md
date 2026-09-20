# Supabricks publication identity protocol

The pinned Supabricks local-owner profile adds an optional
`X-Supabricks-Table-Id: <UUID>` header to the existing authenticated table APIs.
Existing clients retain the ordinary UC behavior.

- `POST /api/2.1/unity-catalog/tables`: assigns the supplied UUID only to an
  EXTERNAL DELTA table. Namespace authorization is unchanged. Name collisions
  are rejected. A UUID reservation is committed in the same transaction as the
  table and persists after deletion. Reusing a UUID is rejected, including at a
  different name. A lost create response is reconciled by GET and an exact UUID,
  schema and location comparison; POST is never an upsert.
- `DELETE /api/2.1/unity-catalog/tables/{full_name}`: when the header is supplied,
  locks the table row and checks its UUID and EXTERNAL type in the deletion
  transaction. A mismatch is rejected. Only catalog metadata is removed; external
  files and the UUID reservation remain. No cascade is performed.

The additive `sb_publication_identities` table is created through UC's existing
Hibernate schema initialization. It is internal provider state, not an editable
UC property. Backups must preserve it with the metastore. This profile does not
provide multiuser isolation or qualify arbitrary external UC servers.

`PublicationIdentityTest` exercises duplicate create/lost-response reconciliation,
UUID reuse after deletion, conditional delete, alias recreation, and external
file preservation. `SdkTableCRUDTest` covers ordinary client compatibility.
