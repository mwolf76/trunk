package org.blackcat.trunk.eventbus.data;

public enum QueryType {
    /* user queries */
    FIND_CREATE_USER("find-create-user"),

    /* share queries */
    FIND_UPDATE_SHARE("find-update-share"),
    FIND_SHARE("find-share");

    private final String tag;
    public String getTag() {
        return tag;
    }

    QueryType(String tag) {
        this.tag = tag;
    }
}
