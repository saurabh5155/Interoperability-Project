package com.healthcare.interop.mapping.engine;

import com.healthcare.interop.common.model.MappingTemplate;
import com.healthcare.interop.common.model.ValidationResult;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RulesValidationResult {
    private boolean valid;
    private List<MappingTemplate.FieldMapping> acceptedMappings;
    private List<ValidationResult.ValidationError> errors;
    private List<String> warnings;
}
