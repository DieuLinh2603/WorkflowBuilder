package com.company.workflowbuilder.service.runtime;

import com.company.workflowbuilder.dto.CalculatedOutput;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.*;

/** Small arithmetic language; never executes scripts or accesses objects outside the supplied rows. */
public final class OutputCalculator {
    private OutputCalculator() {}
    private interface Expression { BigDecimal evaluate(Map<String, Object> row, List<Map<String, Object>> rows); }

    public static void validate(List<CalculatedOutput> outputs) {
        if (outputs == null) return;
        if (outputs.size() > 50) throw new IllegalArgumentException("Tối đa 50 cột tính toán");
        Set<String> keys = new HashSet<>();
        for (var output : outputs) {
            if (output == null || output.fieldKey() == null || !output.fieldKey().matches("[a-zA-Z][a-zA-Z0-9_]{0,99}"))
                throw new IllegalArgumentException("Mã cột phải bắt đầu bằng chữ, chỉ gồm chữ, số và dấu gạch dưới (tối đa 100 ký tự)");
            if (!keys.add(output.fieldKey())) throw new IllegalArgumentException("Trùng mã cột: " + output.fieldKey());
            if (output.label() == null || output.label().isBlank() || output.label().length() > 200)
                throw new IllegalArgumentException("Tên cột phải có từ 1 đến 200 ký tự");
            new Parser(output.formula()).parse();
        }
    }

    public static List<Map<String, Object>> calculate(List<CalculatedOutput> outputs, List<Map<String, Object>> input) {
        validate(outputs);
        List<Map<String, Object>> rows = input.stream().map(row -> (Map<String, Object>) new LinkedHashMap<>(row)).toList();
        List<Map<String, Object>> results = input.stream().map(row -> (Map<String, Object>) new LinkedHashMap<String, Object>()).toList();
        if (outputs == null) return results;
        for (var output : outputs) {
            if (rows.stream().anyMatch(row -> row.containsKey(output.fieldKey())))
                throw new IllegalArgumentException("Mã cột đã tồn tại, hãy đặt mã output mới: " + output.fieldKey());
            Expression expression = new Parser(output.formula()).parse();
            List<BigDecimal> values = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) {
                try {
                    BigDecimal value = expression.evaluate(rows.get(i), rows).stripTrailingZeros();
                    values.add(value.scale() < 0 ? value.setScale(0) : value);
                }
                catch (RuntimeException exception) {
                    throw new IllegalArgumentException("Cột " + output.label() + ", dòng " + (i + 1) + ": " + exception.getMessage(), exception);
                }
            }
            for (int i = 0; i < rows.size(); i++) {
                rows.get(i).put(output.fieldKey(), values.get(i));
                results.get(i).put(output.fieldKey(), values.get(i));
            }
        }
        return results;
    }

    private static BigDecimal number(Object value) {
        if (value == null || value.toString().isBlank()) throw new IllegalArgumentException("Cột nguồn trống hoặc không tồn tại");
        try { return new BigDecimal(value.toString(), MathContext.DECIMAL64); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException("Cột nguồn phải chứa số"); }
    }

    private static final class Field implements Expression {
        private final String key;
        Field(String key) { this.key = key; }
        public BigDecimal evaluate(Map<String, Object> row, List<Map<String, Object>> rows) { return number(row.get(key)); }
    }

    private static final class Parser {
        private final String text;
        private int position;
        private int depth;
        private boolean aggregate;
        Parser(String text) {
            if (text == null || text.isBlank() || text.length() > 1000)
                throw new IllegalArgumentException("Công thức phải có từ 1 đến 1000 ký tự");
            this.text = text;
        }
        Expression parse() {
            Expression expression = sum();
            whitespace();
            if (position != text.length()) throw error("Ký tự không hợp lệ");
            return expression;
        }
        private Expression sum() {
            Expression left = product();
            while (true) {
                if (take('+')) { Expression a = left, b = product(); left = (r, rs) -> a.evaluate(r, rs).add(b.evaluate(r, rs), MathContext.DECIMAL64); }
                else if (take('-')) { Expression a = left, b = product(); left = (r, rs) -> a.evaluate(r, rs).subtract(b.evaluate(r, rs), MathContext.DECIMAL64); }
                else return left;
            }
        }
        private Expression product() {
            Expression left = atom();
            while (true) {
                if (take('*') || take('×')) { Expression a = left, b = atom(); left = (r, rs) -> a.evaluate(r, rs).multiply(b.evaluate(r, rs), MathContext.DECIMAL64); }
                else if (take('/') || take('÷')) { Expression a = left, b = atom(); left = (r, rs) -> {
                    BigDecimal divisor = b.evaluate(r, rs);
                    if (divisor.signum() == 0) throw new IllegalArgumentException("Không thể chia cho 0");
                    return a.evaluate(r, rs).divide(divisor, MathContext.DECIMAL64);
                }; }
                else return left;
            }
        }
        private Expression atom() {
            if (++depth > 20) throw error("Công thức lồng quá sâu");
            try {
                if (take('+')) return atom();
                if (take('-')) { Expression value = atom(); return (r, rs) -> value.evaluate(r, rs).negate(); }
                if (take('(')) { Expression value = sum(); expect(')'); return value; }
                if (take('[')) {
                    int start = position;
                    while (position < text.length() && text.charAt(position) != ']') position++;
                    String key = text.substring(start, position).trim();
                    if (key.isEmpty() || key.startsWith("_")) throw error("Mã cột nguồn không hợp lệ");
                    expect(']'); return new Field(key);
                }
                whitespace();
                int start = position;
                while (position < text.length() && Character.isLetter(text.charAt(position))) position++;
                if (position > start) {
                    String function = text.substring(start, position).toUpperCase(Locale.ROOT);
                    if (!Set.of("SUM", "AVG", "AVERAGE").contains(function)) throw error("Chỉ hỗ trợ SUM, AVG, AVERAGE");
                    if (aggregate) throw error("Không lồng hàm tổng hợp bên trong hàm tổng hợp");
                    aggregate = true;
                    expect('('); Expression value = sum(); expect(')');
                    aggregate = false;
                    BigDecimal[] cached = new BigDecimal[1];
                    return (row, rows) -> {
                        if (cached[0] != null) return cached[0];
                        List<BigDecimal> numbers = new ArrayList<>();
                        for (Map<String, Object> item : rows) {
                            if (value instanceof Field field && item.get(field.key) instanceof Collection<?> list)
                                list.forEach(entry -> numbers.add(number(entry)));
                            else numbers.add(value.evaluate(item, rows));
                        }
                        if (numbers.isEmpty()) throw new IllegalArgumentException("Không có dữ liệu để tổng hợp");
                        BigDecimal total = numbers.stream().reduce(BigDecimal.ZERO, (a, b) -> a.add(b, MathContext.DECIMAL64));
                        cached[0] = function.equals("SUM") ? total : total.divide(BigDecimal.valueOf(numbers.size()), MathContext.DECIMAL64);
                        return cached[0];
                    };
                }
                while (position < text.length() && (Character.isDigit(text.charAt(position)) || text.charAt(position) == '.')) position++;
                if (start == position) throw error("Thiếu số, [mã_cột] hoặc biểu thức");
                BigDecimal literal = number(text.substring(start, position));
                return (r, rs) -> literal;
            } finally { depth--; }
        }
        private void whitespace() { while (position < text.length() && Character.isWhitespace(text.charAt(position))) position++; }
        private boolean take(char ch) { whitespace(); if (position < text.length() && text.charAt(position) == ch) { position++; return true; } return false; }
        private void expect(char ch) { if (!take(ch)) throw error("Thiếu '" + ch + "'"); }
        private IllegalArgumentException error(String message) { return new IllegalArgumentException(message + " tại vị trí " + (position + 1)); }
    }
}
