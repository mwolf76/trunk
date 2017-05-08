package org.blackcat.trunk.resource.impl;

import org.blackcat.trunk.resource.Resource;

public abstract class BaseResource implements Resource {

    protected String name;
    private boolean modified = true;

    /* instantiable only by subclassing */
    protected BaseResource()
    {}

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    public boolean isModified() {
        return modified;
    }
    public void setModified(boolean modified) {
        this.modified = modified;
    }

    @Override
    public int compareTo(Resource o) {
        int ordering = this.getOrdering() - o.getOrdering();
        if (ordering != 0)
            return ordering;

        return this.name.compareToIgnoreCase(((BaseResource) o).name);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;

        if (obj == null)
            return false;

        if (getClass() != obj.getClass())
            return false;

        BaseResource other = (BaseResource) obj;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;

        return true;
    }
}
