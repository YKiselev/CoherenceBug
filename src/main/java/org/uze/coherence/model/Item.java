package org.uze.coherence.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * @author Yuriy Kiselev (uze@yandex.ru).
 */
public final class Item implements Serializable {

    private static final long serialVersionUID = 1598581650977142105L;

    private String id;

    private String payload;

    public Item() {
    }

    public Item(String id, String payload) {
        this.id = id;
        this.payload = payload;
    }

    public String id() {
        return id;
    }

    public String payload() {
        return payload;
    }

    @Override
    public String toString() {
        return "Item{" +
                "id='" + id + '\'' +
                ", payload='" + payload + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Item item = (Item) o;
        return Objects.equals(id, item.id) &&
                Objects.equals(payload, item.payload);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, payload);
    }
}
