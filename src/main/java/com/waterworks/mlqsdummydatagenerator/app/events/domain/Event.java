package com.waterworks.mlqsdummydatagenerator.app.events.domain;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

/**
 * The `Event` class represents a generic event containing headers, a payload, and a list of
 * destinations.
 *
 * @author Edgar Thomson
 * @version 1.0
 */
@Data
@Builder
public class Event {

  private Map<String, Object> headers;
  private String payload;
  private List<String> destinations;
}
