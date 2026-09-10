package com.company.workflowbuilder.service.data;

import com.company.workflowbuilder.entity.data.*;
import com.company.workflowbuilder.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.MissingNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.math.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

@Service @RequiredArgsConstructor
public class PipelineDefinitionEngine {
    private final ObjectMapper mapper;
    private final DataConnectorRepository connectors;
    private final PipelineFileVersionRepository files;
    private final CredentialCipherService cipher;
    @Value("${app.pipeline.max-records:10000}") private int maxRecords;
    @Value("${app.pipeline.query-timeout-seconds:30}") private int queryTimeout;

    public ExecutionResult execute(DataPipeline pipeline) {
        return execute(pipeline, () -> false);
    }

    public ExecutionResult execute(DataPipeline pipeline, BooleanSupplier pauseRequested) {
        try {
            Map<String,Object> definition=mapper.readValue(pipeline.getDefinitionJson(),new TypeReference<>(){});
            PreparedData prepared=prepare(pipeline,definition,pauseRequested);
            checkPaused(pauseRequested);
            List<Map<String,Object>> rows=prepared.rows();
            List<Map<String,Object>> schema=mapper.readValue(pipeline.getOutputSchemaJson(),new TypeReference<>(){});
            rows=projectAndValidate(rows,schema,pipeline.getBusinessKey(),pauseRequested); guard(rows,"VALIDATION");
            return new ExecutionResult(rows,prepared.inputCount(),prepared.sourceCount(),detectSchema(rows));
        }catch(PipelinePausedException ex){throw ex;}catch(StageException ex){throw ex;}catch(Exception ex){throw new StageException("DEFINITION",ex.getMessage(),ex);}
    }

    /** Loads sources and applies joins/transforms without requiring an output schema yet. */
    public ExecutionResult discover(DataPipeline pipeline,Object rawDefinition) {
        try {
            Map<String,Object> definition=mapper.convertValue(rawDefinition,new TypeReference<>(){});
            PreparedData prepared=prepare(pipeline,definition,()->false);
            return new ExecutionResult(prepared.rows(),prepared.inputCount(),prepared.sourceCount(),detectSchema(prepared.rows()));
        }catch(StageException ex){throw ex;}catch(Exception ex){throw new StageException("DEFINITION",ex.getMessage(),ex);}
    }

    private PreparedData prepare(DataPipeline pipeline,Map<String,Object> definition,BooleanSupplier pauseRequested) {
        List<Map<String,Object>> sourceDefs=listOfMaps(definition.get("sources"));
        if(sourceDefs.isEmpty()) throw new StageException("SOURCE","At least one source is required");
        Map<String,List<Map<String,Object>>> loaded=new LinkedHashMap<>(); int input=0;
        for(Map<String,Object> source:sourceDefs){checkPaused(pauseRequested);String alias=req(source,"alias");if(loaded.containsKey(alias))throw new StageException("SOURCE","Duplicate alias: "+alias);List<Map<String,Object>> rows=loadSource(pipeline,source,pauseRequested);loaded.put(alias,rows);input+=rows.size();}
        List<Map<String,Object>> rows=new ArrayList<>(loaded.get(sourceDefs.get(0).get("alias").toString()));
        for(Map<String,Object> join:listOfMaps(definition.get("joins"))){checkPaused(pauseRequested);String right=req(join,"rightAlias");if(!loaded.containsKey(right))throw new StageException("JOIN","Unknown rightAlias: "+right);rows=join(rows,loaded.get(right),join);guard(rows,"JOIN");}
        rows=transform(rows,listOfMaps(definition.get("transforms")),pauseRequested);
        return new PreparedData(rows,input,sourceDefs.size());
    }

    private List<Map<String,Object>> loadSource(DataPipeline pipeline,Map<String,Object> source,BooleanSupplier pauseRequested) {
        String type=String.valueOf(source.getOrDefault("type","")).toUpperCase(Locale.ROOT);
        try { return switch(type){case "CSV"->loadCsv(pipeline,source);case "REST"->loadRest(source,pauseRequested);case "POSTGRESQL"->loadPostgres(source);default->throw new StageException("SOURCE","Unsupported source type: "+type);}; }
        catch(StageException ex){throw ex;}catch(Exception ex){throw new StageException("SOURCE",req(source,"alias")+": "+ex.getMessage(),ex);}
    }
    private List<Map<String,Object>> loadCsv(DataPipeline pipeline,Map<String,Object> source){UUID id=UUID.fromString(req(source,"fileVersionId"));PipelineFileVersion file=files.findById(id).orElseThrow(()->new StageException("SOURCE","CSV file version not found"));if(file.getPipeline()!=null&&pipeline.getId()!=null&&!pipeline.getId().equals(file.getPipeline().getId()))throw new StageException("SOURCE","CSV file version belongs to another pipeline");return parseCsv(new String(file.getContentBytes(),StandardCharsets.UTF_8),String.valueOf(source.getOrDefault("delimiter",",")));}
    private List<Map<String,Object>> parseCsv(String content,String delimiter){char sep=delimiter.isEmpty()?',':delimiter.charAt(0);List<List<String>> lines=new ArrayList<>();List<String> line=new ArrayList<>();StringBuilder cell=new StringBuilder();boolean quoted=false;for(int i=0;i<content.length();i++){char c=content.charAt(i);if(c=='"'){if(quoted&&i+1<content.length()&&content.charAt(i+1)=='"'){cell.append('"');i++;}else quoted=!quoted;}else if(c==sep&&!quoted){line.add(cell.toString());cell.setLength(0);}else if((c=='\n'||c=='\r')&&!quoted){if(c=='\r'&&i+1<content.length()&&content.charAt(i+1)=='\n')i++;line.add(cell.toString());cell.setLength(0);if(line.stream().anyMatch(v->!v.isBlank()))lines.add(line);line=new ArrayList<>();}else cell.append(c);}line.add(cell.toString());if(line.stream().anyMatch(v->!v.isBlank()))lines.add(line);if(lines.isEmpty())return List.of();List<String> headers=lines.get(0).stream().map(String::trim).toList();List<Map<String,Object>> out=new ArrayList<>();for(int i=1;i<lines.size();i++){Map<String,Object> row=new LinkedHashMap<>();for(int j=0;j<headers.size();j++)row.put(headers.get(j),j<lines.get(i).size()?lines.get(i).get(j):"");out.add(row);guard(out,"SOURCE");}return out;}
    private List<Map<String,Object>> loadRest(Map<String,Object> source,BooleanSupplier pauseRequested)throws Exception{
        DataConnector connector=connector(source,"REST");
        Map<String,Object> config=json(connector.getConfigJson()),cred=json(cipher.decrypt(connector.getEncryptedCredentials()));
        String base=String.valueOf(config.get("baseUrl"));
        Map<String,Object> pg=map(source.get("pagination"));
        String type=String.valueOf(pg.getOrDefault("type","NONE")).toUpperCase();
        List<Map<String,Object>> out=new ArrayList<>();
        Set<String> pageBodies=new HashSet<>();
        String cursor=null;
        for(int page=0;page<1000;page++){
            checkPaused(pauseRequested);
            String url=buildUrl(base,source,pg,page,cursor);
            HttpRequest.Builder b=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(queryTimeout)).GET();
            String auth=String.valueOf(cred.getOrDefault("authType","NONE"));
            if("BEARER".equalsIgnoreCase(auth))b.header("Authorization","Bearer "+cred.get("token"));
            if("API_KEY".equalsIgnoreCase(auth))b.header(String.valueOf(cred.getOrDefault("headerName","X-API-Key")),String.valueOf(cred.get("apiKey")));
            HttpResponse<String> response=HttpClient.newHttpClient().send(b.build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()>=400)throw new StageException("SOURCE","REST HTTP "+response.statusCode());
            if(!"NONE".equals(type)&&!pageBodies.add(response.body()))throw new StageException("SOURCE","REST pagination returned the same page again. Check the page/size, offset/limit, or cursor parameter names.");
            JsonNode root=mapper.readTree(response.body());
            String recordPath=String.valueOf(source.getOrDefault("recordPath","")).trim();
            JsonNode records=resolveRestRecords(root,recordPath);
            int before=out.size();
            for(JsonNode n:records){if(!n.isObject())throw new StageException("SOURCE","REST records must be JSON objects");out.add(mapper.convertValue(n,new TypeReference<>(){}));guard(out,"SOURCE");}
            if("NONE".equals(type)||out.size()==before)break;
            if("CURSOR".equals(type)){JsonNode node=path(root,String.valueOf(pg.getOrDefault("nextCursorPath","nextCursor")));if(node.isMissingNode()||node.isNull()||node.asText().isBlank())break;cursor=node.asText();}
            if(records.size()<integer(pg.getOrDefault("pageSize",100)))break;
        }
        return out;
    }
    private JsonNode resolveRestRecords(JsonNode root,String recordPath){
        JsonNode selected=recordPath.isBlank()?root:path(root,recordPath);
        if(selected.isArray())return selected;
        if(selected.isMissingNode()||selected.isNull())
            throw new StageException("SOURCE","Không tìm thấy danh sách dữ liệu tại đường dẫn '"+recordPath+"'.");
        if(selected.isObject()){
            Map<String,JsonNode> arrays=new LinkedHashMap<>();
            findArrays(selected,"",arrays,0);
            if(arrays.size()==1)return arrays.values().iterator().next();
            if(arrays.isEmpty()&&recordPath.isBlank())return mapper.createArrayNode().add(selected);
            if(arrays.isEmpty())throw new StageException("SOURCE","Trường '"+recordPath+"' là một object, không phải danh sách. Hãy nhập đường dẫn đến mảng dữ liệu.");
            String prefix=recordPath.isBlank()?"":recordPath+".";
            String suggestions=arrays.keySet().stream().limit(5).map(value->prefix+value).reduce((a,b)->a+", "+b).orElse("");
            throw new StageException("SOURCE","API trả về nhiều danh sách. Hãy nhập một đường dẫn dữ liệu cụ thể, ví dụ: "+suggestions);
        }
        throw new StageException("SOURCE","Đường dẫn dữ liệu phải trỏ đến một mảng JSON hoặc object bản ghi.");
    }
    private void findArrays(JsonNode node,String current,Map<String,JsonNode> arrays,int depth){
        if(depth>5||!node.isObject())return;
        node.fields().forEachRemaining(entry->{
            String next=current.isBlank()?entry.getKey():current+"."+entry.getKey();
            if(entry.getValue().isArray())arrays.put(next,entry.getValue());
            else if(entry.getValue().isObject())findArrays(entry.getValue(),next,arrays,depth+1);
        });
    }
    private String buildUrl(String base,Map<String,Object>s,Map<String,Object>p,int page,String cursor){String type=String.valueOf(p.getOrDefault("type","NONE")).toUpperCase();Map<String,Object> q=new LinkedHashMap<>(map(s.get("queryParams")));if("PAGE".equals(type)){q.put(String.valueOf(p.getOrDefault("pageParam","page")),integer(p.getOrDefault("startPage",1))+page);q.put(String.valueOf(p.getOrDefault("sizeParam","size")),integer(p.getOrDefault("pageSize",100)));}if("OFFSET".equals(type)){q.put(String.valueOf(p.getOrDefault("offsetParam","offset")),page*integer(p.getOrDefault("pageSize",100)));q.put(String.valueOf(p.getOrDefault("limitParam","limit")),integer(p.getOrDefault("pageSize",100)));}if("CURSOR".equals(type)&&cursor!=null)q.put(String.valueOf(p.getOrDefault("cursorParam","cursor")),cursor);if(q.isEmpty())return base;StringJoiner j=new StringJoiner("&",base.contains("?")?"&":"?","");q.forEach((k,v)->j.add(URLEncoder.encode(k,StandardCharsets.UTF_8)+"="+URLEncoder.encode(String.valueOf(v),StandardCharsets.UTF_8)));return base+j;}
    private List<Map<String,Object>> loadPostgres(Map<String,Object> source)throws Exception{DataConnector c=connector(source,"POSTGRESQL");Map<String,Object> config=json(c.getConfigJson()),cred=json(cipher.decrypt(c.getEncryptedCredentials()));String sql=req(source,"query").trim();String normalized=sql.replaceAll("(?s)/\\*.*?\\*/|--.*?(\\R|$)","").trim().toLowerCase();if(!normalized.startsWith("select ")||normalized.contains(";"))throw new StageException("SOURCE","PostgreSQL source accepts one SELECT statement only");List<Map<String,Object>> out=new ArrayList<>();try(Connection cn=DriverManager.getConnection(String.valueOf(config.get("jdbcUrl")),String.valueOf(cred.get("username")),String.valueOf(cred.get("password")))){cn.setReadOnly(true);try(Statement st=cn.createStatement()){st.setQueryTimeout(queryTimeout);st.setMaxRows(maxRecords+1);try(ResultSet rs=st.executeQuery(sql)){ResultSetMetaData md=rs.getMetaData();while(rs.next()){Map<String,Object> row=new LinkedHashMap<>();for(int i=1;i<=md.getColumnCount();i++)row.put(md.getColumnLabel(i),rs.getObject(i));out.add(row);guard(out,"SOURCE");}}}}return out;}
    private DataConnector connector(Map<String,Object>s,String expected){DataConnector c=connectors.findById(UUID.fromString(req(s,"connectorId"))).orElseThrow(()->new StageException("SOURCE","Connector not found"));if(!c.isActive()||!expected.equals(c.getConnectorType()))throw new StageException("SOURCE","Connector type mismatch or disabled");return c;}

    private List<Map<String,Object>> join(List<Map<String,Object>> left,List<Map<String,Object>> right,Map<String,Object>d){List<String> lk=strings(d.get("leftKeys")),rk=strings(d.get("rightKeys"));if(lk.isEmpty()||lk.size()!=rk.size())throw new StageException("JOIN","Join keys must have equal non-zero size");validateJoinKeys(left,lk,"main source");validateJoinKeys(right,rk,"right source");String cardinality=String.valueOf(d.getOrDefault("cardinality","ONE_TO_ONE"));Map<String,List<Map<String,Object>>> index=index(right,rk);Optional<String>rightDuplicate=duplicateKey(index),leftDuplicate=duplicateKey(index(left,lk));String alias=req(d,"rightAlias");if(("ONE_TO_ONE".equals(cardinality)||"MANY_TO_ONE".equals(cardinality))&&rightDuplicate.isPresent())throw new StageException("JOIN","Join '"+alias+"' has duplicate key on the right "+rk+" = "+readableKey(rightDuplicate.get())+". Choose ONE_TO_MANY or correct the source data.");if(("ONE_TO_ONE".equals(cardinality)||"ONE_TO_MANY".equals(cardinality))&&leftDuplicate.isPresent())throw new StageException("JOIN","Join '"+alias+"' has duplicate key on the main source "+lk+" = "+readableKey(leftDuplicate.get())+". Check whether the main source was paginated repeatedly or choose MANY_TO_ONE when appropriate.");String type=String.valueOf(d.getOrDefault("type","INNER")).toUpperCase();if(!Set.of("INNER","LEFT","FULL").contains(type))throw new StageException("JOIN","Unsupported join type: "+type);String prefix=String.valueOf(d.getOrDefault("rightPrefix",alias+"."));if(prefix.isBlank())prefix=alias+".";Set<String>leftColumns=columns(left),rightColumns=columns(right);List<Map<String,Object>> out=new ArrayList<>();Set<Map<String,Object>> matched=Collections.newSetFromMap(new IdentityHashMap<>());for(Map<String,Object> l:left){List<Map<String,Object>> matches=index.getOrDefault(key(l,lk),List.of());if(matches.isEmpty()){if("LEFT".equals(type)||"FULL".equals(type)){Map<String,Object>m=new LinkedHashMap<>(l);appendRight(m,null,rightColumns,prefix);out.add(m);}}else for(Map<String,Object> r:matches){Map<String,Object> m=new LinkedHashMap<>(l);appendRight(m,r,rightColumns,prefix);out.add(m);matched.add(r);}}if("FULL".equals(type))for(Map<String,Object> r:right)if(!matched.contains(r)){Map<String,Object>m=new LinkedHashMap<>();leftColumns.forEach(column->m.put(column,null));appendRight(m,r,rightColumns,prefix);out.add(m);}return out;}
    private Set<String> columns(List<Map<String,Object>>rows){Set<String>columns=new LinkedHashSet<>();rows.forEach(row->columns.addAll(row.keySet()));return columns;}
    private void appendRight(Map<String,Object>target,Map<String,Object>right,Set<String>columns,String prefix){for(String column:columns)target.put(prefix+column,right==null?null:right.get(column));}
    private Map<String,List<Map<String,Object>>> index(List<Map<String,Object>> rows,List<String>keys){Map<String,List<Map<String,Object>>> out=new LinkedHashMap<>();for(Map<String,Object>r:rows)out.computeIfAbsent(key(r,keys),x->new ArrayList<>()).add(r);return out;}private Optional<String>duplicateKey(Map<String,List<Map<String,Object>>>indexed){return indexed.entrySet().stream().filter(entry->entry.getValue().size()>1).map(Map.Entry::getKey).findFirst();}private String readableKey(String value){return value.replace('\u001f',',');}private String key(Map<String,Object>row,List<String>keys){StringJoiner s=new StringJoiner("\u001f");for(String k:keys)s.add(Objects.toString(joinValue(row,k),"<null>"));return s.toString();}
    private void validateJoinKeys(List<Map<String,Object>>rows,List<String>keys,String side){if(rows.isEmpty())return;for(String key:keys)if(rows.stream().noneMatch(row->hasJoinKey(row,key)))throw new StageException("JOIN","Join column '"+key+"' was not found on the "+side+". Available columns: "+rows.get(0).keySet());}
    private boolean hasJoinKey(Map<String,Object>row,String field){if(row.containsKey(field))return true;int dot=field.indexOf('.');return dot>=0&&dot+1<field.length()&&row.containsKey(field.substring(dot+1));}
    private Object joinValue(Map<String,Object>row,String field){if(row.containsKey(field))return row.get(field);int dot=field.indexOf('.');return dot>=0&&dot+1<field.length()?row.get(field.substring(dot+1)):null;}

    private List<Map<String,Object>> transform(List<Map<String,Object>> input,List<Map<String,Object>> defs,BooleanSupplier pauseRequested){
        List<Map<String,Object>> rows=input.stream().map(LinkedHashMap::new).map(x->(Map<String,Object>)x).toList();
        rows=new ArrayList<>(rows);
        for(Map<String,Object>d:defs){
            checkPaused(pauseRequested);
            String type=req(d,"type").toUpperCase();
            if("FILTER".equals(type)){Predicate<Map<String,Object>> p=r->condition(r,d);rows.removeIf(p.negate());continue;}
            if(Set.of("SUM","AVERAGE").contains(type)){rows=aggregate(rows,d,type);continue;}
            for(Map<String,Object>r:rows){
                checkPaused(pauseRequested);
                String field=String.valueOf(d.getOrDefault("field",""));Object value=fieldValue(r,field);
                switch(type){
                    case"RENAME"->{r.put(req(d,"to"),value);r.remove(field);}
                    case"CAST"->r.put(field,cast(value,req(d,"targetType")));
                    case"TRIM"->r.put(field,value==null?null:String.valueOf(value).trim());
                    case"UPPER"->r.put(field,value==null?null:String.valueOf(value).toUpperCase(Locale.ROOT));
                    case"LOWER"->r.put(field,value==null?null:String.valueOf(value).toLowerCase(Locale.ROOT));
                    case"DEFAULT"->{if(value==null||String.valueOf(value).isBlank())r.put(field,d.get("value"));}
                    case"CONCAT"->{StringBuilder b=new StringBuilder();String sep=String.valueOf(d.getOrDefault("separator",""));for(String f:strings(d.get("fields"))){if(b.length()>0)b.append(sep);b.append(Objects.toString(fieldValue(r,f),""));}r.put(req(d,"target"),b.toString());}
                    case"ADD","SUBTRACT","MULTIPLY","DIVIDE"->r.put(req(d,"target"),arithmetic(r,d,type));
                    case"DERIVE"->r.put(req(d,"target"),condition(r,d)?d.get("thenValue"):d.get("elseValue"));
                    default->throw new StageException("TRANSFORM","Unsupported transform: "+type);
                }
            }
        }
        return rows;
    }
    private List<Map<String,Object>> aggregate(List<Map<String,Object>>rows,Map<String,Object>d,String type){
        String field=req(d,"field"),target=req(d,"target");
        List<String>groupBy=strings(d.get("groupBy"));
        Map<String,Map<String,Object>>result=new LinkedHashMap<>();
        Map<String,BigDecimal>totals=new LinkedHashMap<>();Map<String,Integer>counts=new LinkedHashMap<>();
        for(Map<String,Object>row:rows){
            String groupKey=groupBy.isEmpty()?"ALL":key(row,groupBy);
            result.computeIfAbsent(groupKey,ignored->{Map<String,Object>item=new LinkedHashMap<>();if(groupBy.isEmpty())item.put("_aggregateKey","ALL");else groupBy.forEach(key->item.put(key,fieldValue(row,key)));return item;});
            Object raw=fieldValue(row,field);
            if(raw==null||String.valueOf(raw).isBlank())continue;
            totals.merge(groupKey,decimal(raw),BigDecimal::add);counts.merge(groupKey,1,Integer::sum);
        }
        for(Map.Entry<String,Map<String,Object>>entry:result.entrySet()){
            BigDecimal total=totals.get(entry.getKey());int count=counts.getOrDefault(entry.getKey(),0);
            Object value=total==null?null:"AVERAGE".equals(type)?plainDecimal(total.divide(BigDecimal.valueOf(count),8,RoundingMode.HALF_UP)):plainDecimal(total);
            entry.getValue().put(target,value);
        }
        return new ArrayList<>(result.values());
    }
    private BigDecimal plainDecimal(BigDecimal value){BigDecimal normalized=value.stripTrailingZeros();return normalized.scale()<0?normalized.setScale(0):normalized;}
    private Object arithmetic(Map<String,Object>r,Map<String,Object>d,String op){BigDecimal a=decimal(fieldValue(r,req(d,"leftField"))),b=d.containsKey("rightField")?decimal(fieldValue(r,String.valueOf(d.get("rightField")))):decimal(d.get("value"));return switch(op){case"ADD"->a.add(b);case"SUBTRACT"->a.subtract(b);case"MULTIPLY"->a.multiply(b);default->a.divide(b,8,RoundingMode.HALF_UP).stripTrailingZeros();};}
    private boolean condition(Map<String,Object>r,Map<String,Object>d){Object actual=fieldValue(r,String.valueOf(d.get("field"))),expected=d.get("value");String op=String.valueOf(d.getOrDefault("operator","EQUALS")).toUpperCase();return switch(op){case"EQUALS"->Objects.equals(normal(actual),normal(expected));case"NOT_EQUALS"->!Objects.equals(normal(actual),normal(expected));case"GT"->compare(actual,expected)>0;case"GTE"->compare(actual,expected)>=0;case"LT"->compare(actual,expected)<0;case"LTE"->compare(actual,expected)<=0;case"CONTAINS"->actual!=null&&String.valueOf(actual).contains(String.valueOf(expected));case"IN"->expected instanceof Collection<?>c&&c.stream().map(this::normal).anyMatch(x->Objects.equals(x,normal(actual)));case"IS_EMPTY"->actual==null||String.valueOf(actual).isBlank();case"NOT_EMPTY"->actual!=null&&!String.valueOf(actual).isBlank();default->throw new StageException("TRANSFORM","Unsupported operator: "+op);};}
    private List<Map<String,Object>> projectAndValidate(List<Map<String,Object>> rows,List<Map<String,Object>>schema,String businessKey,BooleanSupplier pauseRequested){List<String> keys=Arrays.stream(businessKey.split(",")).map(String::trim).filter(s->!s.isEmpty()).toList();if(keys.isEmpty())throw new StageException("VALIDATION","Vui lòng chọn khóa định danh duy nhất");String suggestion=schema.stream().filter(field->String.valueOf(field.getOrDefault("source","")).matches(".*\\.id$")).map(field->String.valueOf(field.get("fieldKey"))).findFirst().orElse(null);Set<String> unique=new HashSet<>();List<Map<String,Object>>out=new ArrayList<>();for(int i=0;i<rows.size();i++){checkPaused(pauseRequested);Map<String,Object> projected=new LinkedHashMap<>();for(Map<String,Object>f:schema){String key=req(f,"fieldKey");Object value=fieldValue(rows.get(i),String.valueOf(f.getOrDefault("source",key)));if(Boolean.TRUE.equals(f.get("required"))&&(value==null||String.valueOf(value).isBlank()))throw new StageException("VALIDATION","Dòng "+(i+1)+" thiếu trường bắt buộc: "+key);if(value!=null)value=cast(value,String.valueOf(f.getOrDefault("type","STRING")));projected.put(key,value);}String bk=key(projected,keys);if(bk.contains("<null>")||!unique.add(bk)){String hint=suggestion==null?"":" Với Join một–nhiều, hãy dùng khóa '"+suggestion+"' hoặc một khóa kết hợp.";throw new StageException("VALIDATION","Khóa định danh bị trùng hoặc rỗng ở dòng "+(i+1)+": "+bk+"."+hint);}out.add(projected);}return out;}
    private Object fieldValue(Map<String,Object>row,String field){if(row.containsKey(field))return row.get(field);List<String>matches=row.keySet().stream().filter(key->key.endsWith("."+field)).toList();return matches.size()==1?row.get(matches.get(0)):null;}
    private Object cast(Object v,String type){if(v==null)return null;try{return switch(type.toUpperCase()){case"STRING"->String.valueOf(v);case"INTEGER"->new BigDecimal(String.valueOf(v).trim()).intValueExact();case"DECIMAL","NUMBER"->new BigDecimal(String.valueOf(v).trim());case"BOOLEAN"->{String s=String.valueOf(v).trim();if(!Set.of("true","false","1","0","yes","no").contains(s.toLowerCase()))throw new IllegalArgumentException();yield Set.of("true","1","yes").contains(s.toLowerCase());}case"DATE"->java.time.LocalDate.parse(String.valueOf(v).trim()).toString();case"DATETIME"->java.time.LocalDateTime.parse(String.valueOf(v).trim()).toString();default->throw new IllegalArgumentException("Unknown type "+type);};}catch(Exception ex){throw new StageException("TRANSFORM","Cannot cast '"+v+"' to "+type);}}
    private Map<String,String> detectSchema(List<Map<String,Object>>rows){Map<String,String>s=new LinkedHashMap<>();for(Map<String,Object>r:rows)r.forEach((k,v)->s.putIfAbsent(k,v==null?"UNKNOWN":v instanceof Number?"NUMBER":v instanceof Boolean?"BOOLEAN":"STRING"));return s;}
    public String checksum(Map<String,Object>row){try{Object canonical=canonical(row);byte[] bytes=mapper.writeValueAsBytes(canonical);return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception ex){throw new IllegalStateException(ex);}}private Object canonical(Object v){if(v instanceof Map<?,?>m){Map<String,Object>x=new TreeMap<>();m.forEach((k,val)->x.put(String.valueOf(k),canonical(val)));return x;}if(v instanceof List<?>l)return l.stream().map(this::canonical).toList();return v;}
    private JsonNode path(JsonNode root,String path){JsonNode n=root;if(path==null||path.isBlank())return n;for(String p:path.split("\\.")){if(n==null)return MissingNode.getInstance();n=n.path(p);}return n;}
    private void guard(Collection<?>rows,String stage){if(rows.size()>maxRecords)throw new StageException(stage,"Record limit exceeded: "+maxRecords);}
    private void checkPaused(BooleanSupplier pauseRequested){if(pauseRequested.getAsBoolean())throw new PipelinePausedException();}
    private int compare(Object a,Object b){try{return decimal(a).compareTo(decimal(b));}catch(Exception ex){return String.valueOf(a).compareTo(String.valueOf(b));}}private BigDecimal decimal(Object v){return new BigDecimal(String.valueOf(v).trim());}private Object normal(Object v){return v instanceof Number?new BigDecimal(String.valueOf(v)).stripTrailingZeros():v==null?null:String.valueOf(v);}
    private String req(Map<String,Object>m,String k){String v=String.valueOf(m.getOrDefault(k,"")).trim();if(v.isEmpty())throw new StageException("DEFINITION",k+" is required");return v;}private int integer(Object v){return Integer.parseInt(String.valueOf(v));}private Map<String,Object> map(Object v){return v instanceof Map<?,?>m?mapper.convertValue(m,new TypeReference<>(){}):new LinkedHashMap<>();}private List<Map<String,Object>>listOfMaps(Object v){return v instanceof Collection<?>c?c.stream().map(this::map).toList():List.of();}private List<String>strings(Object v){return v instanceof Collection<?>c?c.stream().map(String::valueOf).toList():List.of();}private Map<String,Object>json(String v){try{return mapper.readValue(v,new TypeReference<>(){});}catch(Exception ex){throw new StageException("SOURCE","Invalid stored JSON",ex);}}
    public record ExecutionResult(List<Map<String,Object>> records,int inputCount,int sourceCount,Map<String,String> detectedSchema){}
    private record PreparedData(List<Map<String,Object>> rows,int inputCount,int sourceCount){}
    public static class StageException extends IllegalArgumentException {
        private final String stage;
        public StageException(String stage,String message){super(message);this.stage=stage;}
        public StageException(String stage,String message,Throwable cause){super(message,cause);this.stage=stage;}
        public String getStage(){return stage;}
    }
    public static class PipelinePausedException extends RuntimeException {}
}
