package com.healthcare.interop.fhir.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.validation.FhirValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FhirConfig {

    @Bean
    public FhirContext fhirContext() {
        FhirContext ctx = FhirContext.forR4();
        ctx.getParserOptions().setStripVersionsFromReferences(false);
        return ctx;
    }

    @Bean
    public FhirValidator fhirValidator(FhirContext fhirContext) {
        FhirValidator validator = fhirContext.newValidator();
        validator.setValidateAgainstStandardSchema(true);
        return validator;
    }
}
