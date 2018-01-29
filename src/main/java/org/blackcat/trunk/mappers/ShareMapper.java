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
    public String toString() {
        return "ShareMapper{" +
                "id='" + id + '\'' +
                ", collectionPath='" + collectionPath + '\'' +
                ", owner=" + owner +
                ", authorizedUsers=" + authorizedUsers +
                '}';
    }
}