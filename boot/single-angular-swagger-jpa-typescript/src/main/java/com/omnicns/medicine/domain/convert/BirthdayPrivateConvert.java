package com.omnicns.medicine.domain.convert;

import com.omnicns.java.security.AES256Coder;
import com.omnicns.medicine.config.properties.ProjectProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.persistence.AttributeConverter;

@Component
@Slf4j
public class BirthdayPrivateConvert implements AttributeConverter<String, String> {

    private static ProjectProperties projectProperties;

    @Autowired
    public void setObjectMapper(ProjectProperties projectProperties) {
        this.projectProperties = projectProperties;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        try {
            String privateKey = projectProperties.getProperties().get("private-key");
            return AES256Coder.encode(privateKey, attribute);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        try {
            String privateKey = projectProperties.getProperties().get("private-key");
            return StringUtils.overlay(AES256Coder.decode(privateKey, dbData), StringUtils.repeat("*", 4), 2, 2+4);
        } catch (Exception e) {
            return null;
        }
    }
}
