package com.waterworks.mlqsdummydatagenerator.app.generators.domain;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class Supplier {
  private String supplierId;
  private String name;
  private String address;
  private String email;
  private String phoneNumber;
}
