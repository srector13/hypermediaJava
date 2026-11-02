package dev.hypermediajava.employee;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Looks up the store for the engine named in the URL. */
@Component
public class EmployeeStores {

    private final Map<Engine, EmployeeStore> stores = new EnumMap<>(Engine.class);

    public EmployeeStores(List<EmployeeStore> all) {
        all.forEach(s -> stores.put(s.engine(), s));
    }

    public EmployeeStore get(Engine engine) {
        return stores.get(engine);
    }
}
