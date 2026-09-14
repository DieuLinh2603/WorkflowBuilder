package com.company.workflowbuilder.service;

import com.company.workflowbuilder.dto.CalculatedOutput;
import com.company.workflowbuilder.service.runtime.OutputCalculator;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class OutputCalculatorTest {
    private CalculatedOutput output(String key, String formula) { return new CalculatedOutput(key, key, formula); }

    @Test void arithmeticRespectsPrecedenceParenthesesAndDecimals() {
        var result = OutputCalculator.calculate(List.of(output("result", "([qty] * [price] - 2) / 4 + -0.5")), List.of(Map.of("qty", 3, "price", "10.5")));
        assertThat((BigDecimal) result.get(0).get("result")).isEqualByComparingTo("6.875");
    }

    @Test void aggregateAndDependentColumnsUseTheEntireBatch() {
        List<Map<String, Object>> source = List.of(Map.of("qty", 2, "price", 10), Map.of("qty", 3, "price", 20));
        var result = OutputCalculator.calculate(List.of(output("amount", "[qty] * [price]"),
                output("total", "SUM([amount])"), output("average", "AVERAGE([amount])"),
                output("ratio", "[amount] / SUM([amount])")), source);
        assertThat((BigDecimal) result.get(0).get("total")).isEqualByComparingTo("80");
        assertThat((BigDecimal) result.get(1).get("average")).isEqualByComparingTo("40");
        assertThat((BigDecimal) result.get(0).get("ratio")).isEqualByComparingTo("0.25");
        assertThat(source.get(0)).doesNotContainKey("amount");
    }

    @Test void totalsCanAggregateAnExpressionOrAnArray() {
        var expression = OutputCalculator.calculate(List.of(output("total", "SUM([a] * [b])")), List.of(Map.of("a", 2, "b", 3), Map.of("a", 4, "b", 5)));
        assertThat((BigDecimal) expression.get(0).get("total")).isEqualByComparingTo("26");
        var array = OutputCalculator.calculate(List.of(output("average", "AVG([scores])")), List.of(Map.of("scores", List.of(2, 4, 9))));
        assertThat((BigDecimal) array.get(0).get("average")).isEqualByComparingTo("5");
    }

    @Test void invalidValuesAndDivisionByZeroNeverProducePartialResults() {
        for (String formula : List.of("[a] / 0", "[missing] + 1", "[text] * 2", "AVG([empty])")) {
            assertThatThrownBy(() -> OutputCalculator.calculate(List.of(output("result", formula)), List.of(Map.of("a", 1, "text", "abc", "empty", List.of())))).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test void rejectsOverwriteDuplicatesReservedNamesAndInvalidSyntax() {
        assertThatThrownBy(() -> OutputCalculator.calculate(List.of(output("a", "2")), List.of(Map.of("a", 1)))).hasMessageContaining("đã tồn tại");
        assertThatThrownBy(() -> OutputCalculator.validate(List.of(output("a", "2"), output("a", "3")))).hasMessageContaining("Trùng");
        for (String formula : List.of("SUM(AVG([a]))", "[a] +", "[a", "1; alert(1)", "[_batch]", "Math.random()"))
            assertThatThrownBy(() -> OutputCalculator.validate(List.of(output("result", formula)))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OutputCalculator.validate(List.of(output("_batch", "1")))).isInstanceOf(IllegalArgumentException.class);
    }
}
