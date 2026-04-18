package com.healthcare.interop.mapping.controller;

import com.healthcare.interop.common.model.MappingTemplate;
import com.healthcare.interop.common.model.TransformationContext;
import com.healthcare.interop.mapping.entity.MappingTemplateEntity;
import com.healthcare.interop.mapping.repository.MappingTemplateRepository;
import com.healthcare.interop.mapping.service.MappingOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/mapping")
@RequiredArgsConstructor
@Slf4j
public class MappingController {

    private final MappingOrchestrator mappingOrchestrator;
    private final MappingTemplateRepository templateRepository;

    @PostMapping("/transform")
    public Mono<ResponseEntity<TransformationContext>> transform(
            @RequestBody TransformationContext context) {
        return mappingOrchestrator.transform(context)
            .map(ResponseEntity::ok);
    }

    @GetMapping("/templates")
    public ResponseEntity<List<MappingTemplateEntity>> listTemplates(
            @RequestParam String sourceEhrCode,
            @RequestParam String targetEhrCode) {
        return ResponseEntity.ok(
            templateRepository.findBySourceEhrCodeAndTargetEhrCode(sourceEhrCode, targetEhrCode));
    }
}
