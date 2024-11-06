package com.waterworks.mlqsdummydatagenerator.app.generators.domain;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class Employee {
    private String employeeId;
    private String name;
    private String position;
    private String email;
    private String phoneNumber;
}
