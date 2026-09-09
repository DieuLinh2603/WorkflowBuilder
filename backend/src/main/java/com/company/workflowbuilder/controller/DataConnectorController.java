package com.company.workflowbuilder.controller;

import com.company.workflowbuilder.service.data.DataConnectorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/connectors") @RequiredArgsConstructor
public class DataConnectorController {
    private final DataConnectorService service;
    @GetMapping public List<Map<String,Object>> list(){return service.list();}
    @GetMapping("/{id}") public Map<String,Object> get(@PathVariable UUID id){return service.viewById(id);}
    @PostMapping public ResponseEntity<Map<String,Object>> create(@RequestBody Map<String,Object> body){return ResponseEntity.status(HttpStatus.CREATED).body(service.save(null,body));}
    @PutMapping("/{id}") public Map<String,Object> update(@PathVariable UUID id,@RequestBody Map<String,Object> body){return service.save(id,body);}
    @PostMapping("/{id}/test") public Map<String,Object> test(@PathVariable UUID id){return service.test(id);}
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID id){service.delete(id);}
}
