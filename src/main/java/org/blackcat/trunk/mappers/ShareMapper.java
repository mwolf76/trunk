package org.blackcat.trunk.mappers;

import de.braintags.io.vertx.pojomapper.annotation.Entity;
import de.braintags.io.vertx.pojomapper.annotation.field.Id;
import de.braintags.io.vertx.pojomapper.annotation.field.Referenced;

import java.util.ArrayList;
import java.util.List;

@Entity
final public class ShareMapper {
    @Id
    private String id;
    private String collectionPath;

    @Referenced
    public UserMapper owner;

    private List<String> authorizedUsers;

    public ShareMapper() {
        authorizedUsers = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCollectionPath() {
        return collectionPath;
    }

    public void setCollectionPath(String collectionPath) {
        this.collectionPath = collectionPath;
    }

    public UserMapper getOwner() {
        return owner;
    }

    public void setOwner(UserMapper owner) {
        this.owner = owner;
    }

    public List<String> getAuthorizedUsers() {
        return new ArrayList<>(authorizedUsers);
    }

    public void setAuthorizedUsers(List<String> authorizedUsers) {
        this.authorizedUsers = new ArrayList<>(authorizedUsers);
    }

    public boolean isAuthorized(String user) {
        final String anyone = "*";
        return
                authorizedUsers.contains(anyone) ||
                authorizedUsers.contains(user);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ShareMapper)) return false;

        ShareMapper that = (ShareMapper) o;

        if (id != null ? !id.equals(that.id) : that.id != null) return false;
        if (collectionPath != null ? !collectionPath.equals(that.collectionPath) : that.collectionPath != null)
            return false;
        if (owner != null ? !owner.equals(that.owner) : that.owner != null) return false;
        return authorizedUsers != null ? authorizedUsers.equals(that.authorizedUsers) : that.authorizedUsers == null;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (collectionPath != null ? collectionPath.hashCode() : 0);
        result = 31 * result + (owner != null ? owner.hashCode() : 0);
        result = 31 * result + (authorizedUsers != null ? authorizedUsers.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ShareMapper{" +
                "id='" + id + '\'' +
                ", collectionPath='" + collectionPath + '\'' +
                ", owner=" + owner +
                ", authorizedUsers=" + authorizedUsers +
                '}';
    }
}