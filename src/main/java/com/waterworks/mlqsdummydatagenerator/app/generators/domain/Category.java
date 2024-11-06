package com.waterworks.mlqsdummydatagenerator.app.generators.domain;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class Category {
  private String categoryId;
  private String name;
}
