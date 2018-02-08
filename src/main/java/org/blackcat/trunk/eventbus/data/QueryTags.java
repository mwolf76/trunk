package org.blackcat.trunk.eventbus.data;

public final class QueryTags {
    /** Retrieve a user entity from db. Create a new user entity if requested one does not exist. */
    static final String QRY_FIND_CREATE_USER = "find-create-user";

    /** Retrieve a share entity from db. Returns a new, not persisted, share entity if requested one does not exist.*/
    static final String QRY_FIND_SHARE = "find-share";

    /** Updates an existing share. If requested share entity does not exist, a new one is created and persisted. */
    static final String QRY_UPDATE_SHARE = "find-update-share";
}
