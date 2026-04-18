package com.healthcare.interop.fhir.service;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.healthcare.interop.common.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Optional;

/**
 * Converts generic EHR JSON (normalized from any source) to FHIR R4 Bundle.
 * Uses field mapping applied by mapping-service; this service handles
 * HAPI FHIR model construction and serialization.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GenericToFhirTransformer {

    private final FhirContext fhirContext;

    public JsonNode toFhirPatient(JsonNode sourcePayload) {
        Patient patient = new Patient();

        // ID
        JsonUtils.extractString(sourcePayload, "$.patient.id")
            .ifPresent(patient::setId);

        JsonUtils.extractString(sourcePayload, "$.patient.mrn")
            .ifPresent(mrn -> patient.addIdentifier()
                .setSystem("urn:oid:2.16.840.1.113883.4.1")
                .setValue(mrn));

        // Name
        HumanName name = patient.addName();
        JsonUtils.extractString(sourcePayload, "$.patient.firstName")
            .ifPresent(fn -> name.addGiven(fn));
        JsonUtils.extractString(sourcePayload, "$.patient.lastName")
            .ifPresent(name::setFamily);
        Optional<String> fullName = JsonUtils.extractString(sourcePayload, "$.patient.fullName");
        if (fullName.isPresent() && name.getGiven().isEmpty()) {
            String[] parts = fullName.get().split(" ", 2);
            name.addGiven(parts[0]);
            if (parts.length > 1) name.setFamily(parts[1]);
        }
        name.setUse(HumanName.NameUse.OFFICIAL);

        // DOB
        JsonUtils.extractString(sourcePayload, "$.patient.dateOfBirth")
            .ifPresent(dob -> {
                try {
                    patient.setBirthDate(new SimpleDateFormat("yyyy-MM-dd").parse(
                        dob.replace("/", "-")));
                } catch (Exception e) {
                    log.warn("Could not parse DOB: {}", dob);
                }
            });

        // Gender
        JsonUtils.extractString(sourcePayload, "$.patient.gender")
            .map(g -> switch (g.toLowerCase()) {
                case "male", "m" -> Enumerations.AdministrativeGender.MALE;
                case "female", "f" -> Enumerations.AdministrativeGender.FEMALE;
                default -> Enumerations.AdministrativeGender.UNKNOWN;
            })
            .ifPresent(patient::setGender);

        // Telecom
        JsonUtils.extractString(sourcePayload, "$.patient.phone")
            .ifPresent(phone -> patient.addTelecom()
                .setSystem(ContactPoint.ContactPointSystem.PHONE)
                .setValue(phone)
                .setUse(ContactPoint.ContactPointUse.HOME));

        JsonUtils.extractString(sourcePayload, "$.patient.email")
            .ifPresent(email -> patient.addTelecom()
                .setSystem(ContactPoint.ContactPointSystem.EMAIL)
                .setValue(email));

        // Address
        Address address = patient.addAddress();
        JsonUtils.extractString(sourcePayload, "$.patient.address.street").ifPresent(address::addLine);
        JsonUtils.extractString(sourcePayload, "$.patient.address.city").ifPresent(address::setCity);
        JsonUtils.extractString(sourcePayload, "$.patient.address.state").ifPresent(address::setState);
        JsonUtils.extractString(sourcePayload, "$.patient.address.zip").ifPresent(address::setPostalCode);
        JsonUtils.extractString(sourcePayload, "$.patient.address.country")
            .ifPresent(address::setCountry);

        String serialized = fhirContext.newJsonParser()
            .setPrettyPrint(false)
            .encodeResourceToString(patient);

        return JsonUtils.toJsonNode(serialized);
    }

    public JsonNode toFhirEncounter(JsonNode sourcePayload) {
        Encounter encounter = new Encounter();

        JsonUtils.extractString(sourcePayload, "$.encounter.id").ifPresent(encounter::setId);
        JsonUtils.extractString(sourcePayload, "$.encounter.status")
            .map(s -> switch (s.toLowerCase()) {
                case "planned" -> Encounter.EncounterStatus.PLANNED;
                case "in-progress", "active" -> Encounter.EncounterStatus.INPROGRESS;
                case "finished", "completed" -> Encounter.EncounterStatus.FINISHED;
                default -> Encounter.EncounterStatus.UNKNOWN;
            })
            .ifPresent(encounter::setStatus);

        JsonUtils.extractString(sourcePayload, "$.encounter.patientId")
            .ifPresent(pid -> encounter.setSubject(new Reference("Patient/" + pid)));

        String serialized = fhirContext.newJsonParser().encodeResourceToString(encounter);
        return JsonUtils.toJsonNode(serialized);
    }
}
