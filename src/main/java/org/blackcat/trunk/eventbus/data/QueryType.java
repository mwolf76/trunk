package org.blackcat.trunk.eventbus.data;

import static org.blackcat.trunk.eventbus.data.QueryTags.*;

public enum QueryType {
    /* user queries */
    FIND_CREATE_USER(QRY_FIND_CREATE_USER),

    /* share queries */
    FIND_UPDATE_SHARE(QRY_UPDATE_SHARE),
    FIND_SHARE(QRY_FIND_SHARE);

    private final String tag;
    public String getTag() {
        return tag;
    }

    QueryType(String tag) {
        this.tag = tag;
    }
}


