package com.waterworks.mlqsdummydatagenerator.app.generators.domain;

import lombok.Builder;
import lombok.Data;

/**
 * The `HeaderDTO` class represents a data transfer object for key-value headers.
 *
 * <p>
 * This class is used to encapsulate key-value pairs representing headers. It is commonly used for
 * transmitting header information through a message queue between different components of the sales
 * portal.
 * </p>
 *
 * @author Edgar Thomson
 * @version 1.0
 */
@Data
@Builder
public class Header {

  private String key;
  private String value;
}
