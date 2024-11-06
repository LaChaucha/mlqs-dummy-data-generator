package com.waterworks.mlqsdummydatagenerator.app.generators.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Transaction {
  private String transactionId;
  private String transactionType;
  private Double amount;
  private String transactionDate;
  private String invoiceId;
}
