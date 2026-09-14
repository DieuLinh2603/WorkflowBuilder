package com.company.workflowbuilder.service;

import com.company.workflowbuilder.service.runtime.SimpleJsonPath;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class SimpleJsonPathTest {
    @Test void readsNestedObjectsAndArrays() {
        Object value = Map.of("data", Map.of("items", List.of(Map.of("id", "123"))));
        assertThat(SimpleJsonPath.read(value, "$.data.items[0].id")).contains("123");
        assertThat(SimpleJsonPath.read(value, "$.data.missing")).isEmpty();
    }
}
