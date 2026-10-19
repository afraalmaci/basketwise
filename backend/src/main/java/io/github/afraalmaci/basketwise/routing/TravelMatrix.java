package io.github.afraalmaci.basketwise.routing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Travel legs from every source to every destination: {@code leg(i, j)} is source i to
 * destination j. A leg is {@code null} when the routing provider found no route.
 *
 * @param estimated true when the values come from the straight-line fallback rather than
 *                  a real road network
 */
public record TravelMatrix(List<List<Leg>> legs, boolean estimated) {

    public TravelMatrix {
        List<List<Leg>> copy = new ArrayList<>(legs.size());
        for (List<Leg> row : legs) {
            // Collections.unmodifiableList keeps nulls (List.copyOf would reject them).
            copy.add(Collections.unmodifiableList(new ArrayList<>(row)));
        }
        legs = Collections.unmodifiableList(copy);
    }

    public Leg leg(int source, int destination) {
        return legs.get(source).get(destination);
    }

    public int sourceCount() {
        return legs.size();
    }

    public int destinationCount() {
        return legs.isEmpty() ? 0 : legs.get(0).size();
    }
}
