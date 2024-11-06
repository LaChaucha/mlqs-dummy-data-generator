package com.waterworks.mlqsdummydatagenerator.app.generators.domain;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Order {
  private String orderId;
  private String orderDate;
  private String status;
  private String paymentMethod;
  private String customerId;
  private String sellerId;
  private List<Product> products;
}
