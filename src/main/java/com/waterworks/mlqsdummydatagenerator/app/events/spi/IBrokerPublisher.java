package com.waterworks.mlqsdummydatagenerator.app.events.spi;

import com.waterworks.mlqsdummydatagenerator.app.events.domain.Event;

/**
 * The `IBrokerPublisher` interface defines a contract for sending events to a message broker.
 * Implementations of this interface should provide the functionality to publish events to the
 * broker.
 *
 *  @author Edgar Thomson
 *  @version 1.0
 *
 */
public interface IBrokerPublisher {

  /**
   * Sends an event to a message broker for further distribution.
   *
   * @param event The `Event` object representing the event to be published.
   */
  void sendMessageToBroker(Event event);
}
