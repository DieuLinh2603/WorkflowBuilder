package com.company.workflowbuilder.service;

import com.company.workflowbuilder.entity.workflow.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.*;
import java.time.*;
import java.util.*;

@Service
public class ConditionEvaluatorService {
    private final ObjectMapper mapper;
    public ConditionEvaluatorService(){this(new ObjectMapper());}
    @Autowired public ConditionEvaluatorService(ObjectMapper mapper){this.mapper=mapper;}

    public boolean matches(WorkflowConnection connection,Map<String,Object>fields){
        if(connection.getType()==ConnectionType.ELSE||connection.getType()==ConnectionType.DEFAULT)return true;
        if(connection.getClauses().isEmpty())return false;
        var results=connection.getClauses().stream().map(c->evaluateClause(c,fields));
        return connection.getLogicalOperator()==LogicalOperator.AND?results.allMatch(Boolean::booleanValue):results.anyMatch(Boolean::booleanValue);
    }
    private boolean evaluateClause(WorkflowConditionClause clause,Map<String,Object>fields){
        if(clause.getExpressionJson()!=null&&!clause.getExpressionJson().isBlank())try{return truthy(eval(mapper.readValue(clause.getExpressionJson(),new TypeReference<>(){}),fields,0));}catch(RuntimeException ex){throw ex;}catch(Exception ex){throw new IllegalArgumentException("Invalid stored condition expression",ex);}
        return evaluate(clause,fields.get(clause.getFieldKey()));
    }
    boolean evaluate(WorkflowConditionClause clause,Object actual){return compare(clause.getOperator(),actual,clause.getExpectedValue());}

    private Object eval(Map<String,Object>node,Map<String,Object>fields,int depth){
        if(depth>12)throw new IllegalArgumentException("Condition expression is too deeply nested");
        String type=String.valueOf(node.getOrDefault("type","")).toUpperCase(Locale.ROOT);List<Map<String,Object>>ops=maps(node.get("operands"));
        return switch(type){
            case"FIELD"->fields.get(String.valueOf(node.get("fieldKey")));case"VALUE","LITERAL"->node.get("value");
            case"ADD"->ops.stream().map(x->decimal(eval(x,fields,depth+1))).reduce(BigDecimal.ZERO,BigDecimal::add);
            case"SUBTRACT"->binary(ops,fields,depth,BigDecimal::subtract);case"MULTIPLY"->ops.stream().map(x->decimal(eval(x,fields,depth+1))).reduce(BigDecimal.ONE,BigDecimal::multiply);
            case"DIVIDE"->binary(ops,fields,depth,(a,b)->a.divide(b,10,RoundingMode.HALF_UP).stripTrailingZeros());case"MOD"->binary(ops,fields,depth,BigDecimal::remainder);
            case"SUM"->values(node,ops,fields,depth).stream().map(this::decimal).reduce(BigDecimal.ZERO,BigDecimal::add);
            case"AVG"->{List<Object>v=values(node,ops,fields,depth);yield v.isEmpty()?BigDecimal.ZERO:v.stream().map(this::decimal).reduce(BigDecimal.ZERO,BigDecimal::add).divide(BigDecimal.valueOf(v.size()),10,RoundingMode.HALF_UP).stripTrailingZeros();}
            case"MIN"->values(node,ops,fields,depth).stream().map(this::decimal).min(BigDecimal::compareTo).orElse(null);case"MAX"->values(node,ops,fields,depth).stream().map(this::decimal).max(BigDecimal::compareTo).orElse(null);
            case"COUNT"->values(node,ops,fields,depth).stream().filter(Objects::nonNull).count();case"ABS"->decimal(eval(operand(node,ops),fields,depth+1)).abs();
            case"ROUND"->decimal(eval(operand(node,ops),fields,depth+1)).setScale(integer(node.getOrDefault("scale",0)),RoundingMode.HALF_UP);
            case"COALESCE"->ops.stream().map(x->eval(x,fields,depth+1)).filter(x->x!=null&&!String.valueOf(x).isBlank()).findFirst().orElse(null);
            case"CONCAT"->String.join(String.valueOf(node.getOrDefault("delimiter","")),ops.stream().map(x->Objects.toString(eval(x,fields,depth+1),"")).toList());
            case"LENGTH"->Objects.toString(eval(operand(node,ops),fields,depth+1),"").length();case"TRIM"->Objects.toString(eval(operand(node,ops),fields,depth+1),"").trim();
            case"LOWER"->Objects.toString(eval(operand(node,ops),fields,depth+1),"").toLowerCase(Locale.ROOT);case"UPPER"->Objects.toString(eval(operand(node,ops),fields,depth+1),"").toUpperCase(Locale.ROOT);
            case"COMPARE"->{ConditionOperator op=ConditionOperator.valueOf(String.valueOf(node.get("operator")).toUpperCase());Object left=eval(map(node.get("left")),fields,depth+1),right=node.get("right")==null?null:eval(map(node.get("right")),fields,depth+1);yield compare(op,left,right);}
            case"AND"->children(node).stream().allMatch(x->truthy(eval(x,fields,depth+1)));case"OR"->children(node).stream().anyMatch(x->truthy(eval(x,fields,depth+1)));case"NOT"->!truthy(eval(map(node.get("child")),fields,depth+1));
            default->throw new IllegalArgumentException("Unsupported expression type: "+type);
        };
    }
    private boolean compare(ConditionOperator op,Object actual,Object expected){if(op==null)throw new IllegalArgumentException("Condition operator is required");String a=actual==null?null:String.valueOf(actual),e=expected==null?null:String.valueOf(expected);return switch(op){
        case IS_EMPTY->isEmpty(actual);case NOT_EMPTY->!isEmpty(actual);case IS_NULL->actual==null;case NOT_NULL->actual!=null;case IS_TRUE->truthy(actual);case IS_FALSE->!truthy(actual);
        case EQ->equal(actual,expected);case NEQ->!equal(actual,expected);case GT->order(actual,expected)>0;case GTE->order(actual,expected)>=0;case LT->order(actual,expected)<0;case LTE->order(actual,expected)<=0;
        case CONTAINS->contains(actual,expected);case NOT_CONTAINS->!contains(actual,expected);case STARTS_WITH->a!=null&&a.startsWith(Objects.toString(e,""));case ENDS_WITH->a!=null&&a.endsWith(Objects.toString(e,""));
        case IN->collection(expected).stream().anyMatch(x->equal(actual,x));case NOT_IN->collection(expected).stream().noneMatch(x->equal(actual,x));case BETWEEN->{List<?>r=collection(expected);yield r.size()==2&&order(actual,r.get(0))>=0&&order(actual,r.get(1))<=0;}
    };}
    private boolean contains(Object a,Object e){return a instanceof Collection<?>c?c.stream().anyMatch(x->equal(x,e)):a!=null&&String.valueOf(a).contains(Objects.toString(e,""));}
    private boolean equal(Object a,Object b){if(a==null||b==null)return a==b;try{return decimal(a).compareTo(decimal(b))==0;}catch(Exception ignored){}return String.valueOf(a).equals(String.valueOf(b));}
    private int order(Object a,Object b){try{return decimal(a).compareTo(decimal(b));}catch(Exception ignored){}try{return LocalDateTime.parse(String.valueOf(a)).compareTo(LocalDateTime.parse(String.valueOf(b)));}catch(Exception ignored){}try{return LocalDate.parse(String.valueOf(a)).compareTo(LocalDate.parse(String.valueOf(b)));}catch(Exception ignored){}return String.valueOf(a).compareTo(String.valueOf(b));}
    private BigDecimal binary(List<Map<String,Object>>ops,Map<String,Object>f,int d,java.util.function.BinaryOperator<BigDecimal>fn){if(ops.size()!=2)throw new IllegalArgumentException("Binary expression requires two operands");return fn.apply(decimal(eval(ops.get(0),f,d+1)),decimal(eval(ops.get(1),f,d+1)));}
    private List<Object>values(Map<String,Object>n,List<Map<String,Object>>ops,Map<String,Object>f,int d){if(!ops.isEmpty())return ops.stream().map(x->eval(x,f,d+1)).toList();if(n.get("fields")instanceof Collection<?>c)return c.stream().map(x->f.get(String.valueOf(x))).toList();return List.of();}
    private Map<String,Object>operand(Map<String,Object>n,List<Map<String,Object>>ops){if(n.get("operand")!=null)return map(n.get("operand"));if(ops.size()==1)return ops.get(0);throw new IllegalArgumentException("Expression requires one operand");}
    private boolean truthy(Object v){if(v instanceof Boolean b)return b;if(v instanceof Number n)return decimal(n).compareTo(BigDecimal.ZERO)!=0;return v!=null&&!String.valueOf(v).isBlank()&&!"false".equalsIgnoreCase(String.valueOf(v));}
    private boolean isEmpty(Object v){return v==null||(v instanceof CharSequence s&&s.toString().isBlank())||(v instanceof Collection<?>c&&c.isEmpty());}private BigDecimal decimal(Object v){if(v==null)throw new IllegalArgumentException("Numeric operand is empty");return new BigDecimal(String.valueOf(v).trim());}
    private int integer(Object v){return Integer.parseInt(String.valueOf(v));}private Map<String,Object>map(Object v){return mapper.convertValue(v,new TypeReference<>(){});}private List<Map<String,Object>>maps(Object v){return v instanceof Collection<?>c?c.stream().map(this::map).toList():List.of();}private List<Map<String,Object>>children(Map<String,Object>n){return maps(n.get("children"));}private List<?>collection(Object v){if(v instanceof Collection<?>c)return new ArrayList<>(c);if(v==null)return List.of();return Arrays.stream(String.valueOf(v).split(",")).map(String::trim).toList();}
}
