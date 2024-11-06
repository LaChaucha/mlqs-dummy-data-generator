package com.waterworks.mlqsdummydatagenerator.app.generators.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Invoice {
  private String invoiceId;
  private String issueDate;
  private Double totalAmount;
  private String orderId;
}
