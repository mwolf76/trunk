package org.blackcat.trunk.resource.impl;

import org.blackcat.trunk.resource.Resource;

import java.util.*;

public final class CollectionResource extends BaseResource {

    @Override
    public int getOrdering() {
        return 0;
    }
    private Set<Resource> items;

    private boolean tarball;

    public boolean isTarball() {
        return tarball;
    }

    public void setTarball(boolean tarball) {
        this.tarball = tarball;
    }

    public CollectionResource() {
        items = new TreeSet<>();
    }

    private int size;
    public int getSize() {
        return size;
    }

    public CollectionResource(String name, int size) {
        setName(name);
        this.size = size;
    }

    public Set<Resource> getItems() {
        if (this.items == null)
            return null;

        Set<Resource> res = new TreeSet<>(this.items);
        return res;
    }

    public Integer getItemsCount() {
        if (this.items == null)
            return null;

        return this.items.size();
    }

    public void addItem(Resource item) {
        items.add(item);
    }

    @Override
    public String toString() {
        return "CollectionResource{" +
                "items=" + items +
                '}';
    }
}
