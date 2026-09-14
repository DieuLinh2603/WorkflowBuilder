package com.company.workflowbuilder.service.data;

import com.company.workflowbuilder.entity.data.*;
import com.company.workflowbuilder.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PipelineDefinitionEngineTest {
    final ObjectMapper mapper=new ObjectMapper();
    final DataConnectorRepository connectors=mock(DataConnectorRepository.class);
    final PipelineFileVersionRepository files=mock(PipelineFileVersionRepository.class);
    final CredentialCipherService cipher=mock(CredentialCipherService.class);
    PipelineDefinitionEngine engine;

    @BeforeEach void setup(){engine=new PipelineDefinitionEngine(mapper,connectors,files,cipher);ReflectionTestUtils.setField(engine,"maxRecords",1000);ReflectionTestUtils.setField(engine,"queryTimeout",2);}

    @Test void stopsExecutionWhenPauseIsRequested() throws Exception {
        UUID sourceId=UUID.randomUUID();
        Map<String,Object> definition=Map.of(
                "sources",List.of(Map.of("alias","source","type","CSV","fileVersionId",sourceId)),
                "joins",List.of(),"transforms",List.of());
        DataPipeline pipeline=pipeline(definition,List.of(field("id","STRING",true)),"id");

        assertThatThrownBy(()->engine.execute(pipeline,()->true))
                .isInstanceOf(PipelineDefinitionEngine.PipelinePausedException.class);
        verifyNoInteractions(files);
    }

    @Test void joinsCompositeKeysAndParsesUnicodeCsv()throws Exception{
        UUID peopleId=UUID.randomUUID(),deptId=UUID.randomUUID();
        when(files.findById(peopleId)).thenReturn(Optional.of(file("id,country,name,projects\n1,VN, Nguyễn Văn A ,6\n2,VN,Trần B,0")));
        when(files.findById(deptId)).thenReturn(Optional.of(file("employee,country,department\n1,VN,Kỹ thuật\n2,VN,Nhân sự")));
        Map<String,Object> definition=Map.of(
                "sources",List.of(Map.of("alias","people","type","CSV","fileVersionId",peopleId),Map.of("alias","dept","type","CSV","fileVersionId",deptId)),
                "joins",List.of(Map.of("rightAlias","dept","type","INNER","leftKeys",List.of("id","country"),"rightKeys",List.of("employee","country"),"cardinality","ONE_TO_ONE")),
                "transforms",List.of(Map.of("type","TRIM","field","name"),Map.of("type","CAST","field","projects","targetType","INTEGER")));
        DataPipeline p=pipeline(definition,List.of(field("id","STRING",true),field("name","STRING",true),field("projects","INTEGER",true),field("department","STRING",true)),"id");
        var result=engine.execute(p);
        assertThat(result.records()).hasSize(2);
        assertThat(result.records().get(0)).containsEntry("name","Nguyễn Văn A").containsEntry("projects",6).containsEntry("department","Kỹ thuật");
    }

    @Test void blocksUnexpectedManyToManyJoin()throws Exception{
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();when(files.findById(a)).thenReturn(Optional.of(file("id\n1\n1")));when(files.findById(b)).thenReturn(Optional.of(file("id\n1\n1")));
        Map<String,Object>d=Map.of("sources",List.of(Map.of("alias","a","type","CSV","fileVersionId",a),Map.of("alias","b","type","CSV","fileVersionId",b)),"joins",List.of(Map.of("rightAlias","b","leftKeys",List.of("id"),"rightKeys",List.of("id"),"cardinality","ONE_TO_ONE")));
        assertThatThrownBy(()->engine.execute(pipeline(d,List.of(field("id","STRING",true)),"id"))).isInstanceOf(PipelineDefinitionEngine.StageException.class).hasMessageContaining("duplicate");
    }

    @Test void acceptsSourceQualifiedJoinKeys()throws Exception{
        UUID usersId=UUID.randomUUID(),todosId=UUID.randomUUID();
        when(files.findById(usersId)).thenReturn(Optional.of(file("id,name\n1,An\n2,Binh")));
        when(files.findById(todosId)).thenReturn(Optional.of(file("id,userId,title\n10,1,Task A\n11,1,Task B\n12,2,Task C")));
        Map<String,Object>d=Map.of(
                "sources",List.of(Map.of("alias","Users","type","CSV","fileVersionId",usersId),Map.of("alias","todos","type","CSV","fileVersionId",todosId)),
                "joins",List.of(Map.of("rightAlias","todos","type","INNER","leftKeys",List.of("Users.id"),"rightKeys",List.of("todos.userId"),"cardinality","ONE_TO_MANY")),
                "transforms",List.of());

        var result=engine.discover(pipeline(d,List.of(),"id"),d);

        assertThat(result.records()).hasSize(3);
        assertThat(result.records().get(0)).containsEntry("name","An").containsEntry("todos.userId","1").containsEntry("todos.title","Task A");
    }

    @Test void appliesInnerLeftAndFullJoinSemanticsWithConsistentColumns()throws Exception{
        UUID peopleId=UUID.randomUUID(),deptId=UUID.randomUUID();
        when(files.findById(peopleId)).thenReturn(Optional.of(file("id,name\n1,An\n2,Binh")));
        when(files.findById(deptId)).thenReturn(Optional.of(file("personId,department\n1,Engineering\n3,Finance")));

        var inner=engine.discover(pipeline(joinDefinition(peopleId,deptId,"INNER"),List.of(),"id"),joinDefinition(peopleId,deptId,"INNER"));
        var left=engine.discover(pipeline(joinDefinition(peopleId,deptId,"LEFT"),List.of(),"id"),joinDefinition(peopleId,deptId,"LEFT"));
        var full=engine.discover(pipeline(joinDefinition(peopleId,deptId,"FULL"),List.of(),"id"),joinDefinition(peopleId,deptId,"FULL"));

        assertThat(inner.records()).hasSize(1);
        assertThat(left.records()).hasSize(2);
        assertThat(left.records().get(1)).containsEntry("id","2").containsEntry("dept.department",null);
        assertThat(full.records()).hasSize(3);
        assertThat(full.records().get(2)).containsEntry("id",null).containsEntry("dept.personId","3").containsEntry("dept.department","Finance");
    }

    @Test void reportsUnknownJoinColumnInsteadOfDuplicateNull()throws Exception{
        UUID usersId=UUID.randomUUID(),todosId=UUID.randomUUID();
        when(files.findById(usersId)).thenReturn(Optional.of(file("id,name\n1,An\n2,Binh")));
        when(files.findById(todosId)).thenReturn(Optional.of(file("id,userId\n10,1")));
        Map<String,Object>d=Map.of(
                "sources",List.of(Map.of("alias","Users","type","CSV","fileVersionId",usersId),Map.of("alias","todos","type","CSV","fileVersionId",todosId)),
                "joins",List.of(Map.of("rightAlias","todos","leftKeys",List.of("Users.unknown"),"rightKeys",List.of("todos.userId"),"cardinality","ONE_TO_MANY")));

        assertThatThrownBy(()->engine.discover(pipeline(d,List.of(),"id"),d))
                .isInstanceOf(PipelineDefinitionEngine.StageException.class)
                .hasMessageContaining("Users.unknown").hasMessageContaining("Available columns");
    }

    @Test void calculatesSumByGroup()throws Exception{
        UUID sourceId=UUID.randomUUID();
        when(files.findById(sourceId)).thenReturn(Optional.of(file("department,amount\nA,10\nA,20\nB,5")));
        Map<String,Object> definition=Map.of(
                "sources",List.of(Map.of("alias","sales","type","CSV","fileVersionId",sourceId)),
                "joins",List.of(),
                "transforms",List.of(Map.of("type","SUM","field","amount","groupBy",List.of("department"),"target","total_amount")));

        var result=engine.discover(pipeline(definition,List.of(),"department"),definition);

        assertThat(result.records()).containsExactly(
                new LinkedHashMap<>(Map.of("department","A","total_amount",new java.math.BigDecimal("30"))),
                new LinkedHashMap<>(Map.of("department","B","total_amount",new java.math.BigDecimal("5"))));
    }

    @Test void calculatesAverageWithoutGroupAndAddsStableKey()throws Exception{
        UUID sourceId=UUID.randomUUID();
        when(files.findById(sourceId)).thenReturn(Optional.of(file("amount\n10\n20\n30")));
        Map<String,Object> definition=Map.of(
                "sources",List.of(Map.of("alias","sales","type","CSV","fileVersionId",sourceId)),
                "joins",List.of(),
                "transforms",List.of(Map.of("type","AVERAGE","field","amount","groupBy",List.of(),"target","average_amount")));

        var result=engine.discover(pipeline(definition,List.of(),"_aggregateKey"),definition);

        assertThat(result.records()).singleElement().satisfies(row -> assertThat(row)
                .containsEntry("_aggregateKey","ALL")
                .containsEntry("average_amount",new java.math.BigDecimal("20")));
    }

    @Test void canonicalChecksumDoesNotDependOnMapOrder(){Map<String,Object>a=new LinkedHashMap<>();a.put("name","A");a.put("count",5);Map<String,Object>b=new LinkedHashMap<>();b.put("count",5);b.put("name","A");assertThat(engine.checksum(a)).isEqualTo(engine.checksum(b));}

    @Test void discoversRawFieldsBeforeOutputSchemaIsConfigured()throws Exception{
        UUID sourceId=UUID.randomUUID();
        when(files.findById(sourceId)).thenReturn(Optional.of(file("id,name,active\n1,Nguyen Van A,true")));
        Map<String,Object> definition=Map.of("sources",List.of(Map.of("alias","people","type","CSV","fileVersionId",sourceId)),"joins",List.of(),"transforms",List.of());
        DataPipeline pipeline=pipeline(definition,List.of(),"id");

        var result=engine.discover(pipeline,definition);

        assertThat(result.records()).hasSize(1);
        assertThat(result.records().get(0)).containsEntry("name","Nguyen Van A");
        assertThat(result.detectedSchema()).containsEntry("id","STRING").containsEntry("active","STRING");
    }

    @Test void readsExcelUtf8BomAndAutoDetectsSemicolonDelimiter()throws Exception{
        UUID sourceId=UUID.randomUUID();
        when(files.findById(sourceId)).thenReturn(Optional.of(file("\uFEFFid;name\r\n1;Nguyen Van A")));
        Map<String,Object> definition=Map.of("sources",List.of(Map.of("alias","people","type","CSV","fileVersionId",sourceId,"delimiter","AUTO")),"joins",List.of(),"transforms",List.of());

        var result=engine.discover(pipeline(definition,List.of(),"id"),definition);

        assertThat(result.records()).singleElement().satisfies(row -> assertThat(row)
                .containsEntry("id","1").containsEntry("name","Nguyen Van A")
                .doesNotContainKey("\uFEFFid"));
    }

    @Test void rejectsDuplicateCsvHeadersWithAUsefulMessage()throws Exception{
        UUID sourceId=UUID.randomUUID();
        when(files.findById(sourceId)).thenReturn(Optional.of(file("id,id\n1,2")));
        Map<String,Object> definition=Map.of("sources",List.of(Map.of("alias","people","type","CSV","fileVersionId",sourceId)),"joins",List.of(),"transforms",List.of());

        assertThatThrownBy(()->engine.discover(pipeline(definition,List.of(),"id"),definition))
                .isInstanceOf(PipelineDefinitionEngine.StageException.class).hasMessageContaining("bị trùng");
    }

    @Test void automaticallyFindsTheOnlyNestedRestArray()throws Exception{
        var root=mapper.readTree("{\"data\":{\"items\":[{\"id\":1}]},\"meta\":{\"page\":1}}");
        com.fasterxml.jackson.databind.JsonNode records=ReflectionTestUtils.invokeMethod(engine,"resolveRestRecords",root,"");
        assertThat(records).hasSize(1);
        assertThat(records.get(0).get("id").asInt()).isEqualTo(1);
    }

    @Test void acceptsSingleRestObjectAsOneRecord()throws Exception{
        var root=mapper.readTree("{\"id\":1,\"name\":\"An\"}");
        com.fasterxml.jackson.databind.JsonNode records=ReflectionTestUtils.invokeMethod(engine,"resolveRestRecords",root,"");
        assertThat(records).hasSize(1);
        assertThat(records.get(0).get("name").asText()).isEqualTo("An");
    }

    @Test void explainsAvailablePathsWhenRestResponseHasMultipleArrays()throws Exception{
        var root=mapper.readTree("{\"users\":[],\"roles\":[]}");
        assertThatThrownBy(()->ReflectionTestUtils.invokeMethod(engine,"resolveRestRecords",root,""))
                .hasMessageContaining("users").hasMessageContaining("roles");
    }

    private PipelineFileVersion file(String value){return PipelineFileVersion.builder().contentBytes(value.getBytes(StandardCharsets.UTF_8)).build();}
    private Map<String,Object>joinDefinition(UUID peopleId,UUID deptId,String type){return Map.of("sources",List.of(Map.of("alias","people","type","CSV","fileVersionId",peopleId),Map.of("alias","dept","type","CSV","fileVersionId",deptId)),"joins",List.of(Map.of("rightAlias","dept","type",type,"leftKeys",List.of("id"),"rightKeys",List.of("personId"),"cardinality","ONE_TO_ONE")),"transforms",List.of());}
    private Map<String,Object>field(String key,String type,boolean required){return Map.of("fieldKey",key,"type",type,"required",required);}
    private DataPipeline pipeline(Map<String,Object>d,List<Map<String,Object>>s,String key)throws Exception{return DataPipeline.builder().name("test").businessKey(key).definitionJson(mapper.writeValueAsString(d)).outputSchemaJson(mapper.writeValueAsString(s)).build();}
}
