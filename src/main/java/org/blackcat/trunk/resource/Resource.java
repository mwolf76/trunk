package org.blackcat.trunk.resource;

public interface Resource extends Comparable<Resource> {
    int getOrdering();

    String getName();
    void setName(String name);

    boolean isModified();
    void setModified(boolean modified);
}
