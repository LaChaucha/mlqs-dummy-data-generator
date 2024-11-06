package com.waterworks.mlqsdummydatagenerator.app.generators.domain;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class Customer {
  private String customerId;
  private String name;
  private String address;
  private String email;
  private String phoneNumber;
}
