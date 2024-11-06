package com.waterworks.mlqsdummydatagenerator.app.events;

import com.waterworks.mlqsdummydatagenerator.app.events.domain.Event;
import com.waterworks.mlqsdummydatagenerator.app.events.spi.IBrokerPublisher;
import java.util.Base64;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The `EventsService` class is a service responsible for processing and sending events to a message
 * broker.
 *
 * <p>This class is annotated with `@Service`, indicating that it's a Spring-managed service
 * component. It is also annotated with `@AllArgsConstructor` for constructor-based dependency
 * injection.
 * </p>
 *
 * @author Edgar Thomson
 * @version 1.0
 */
@Service
@AllArgsConstructor
public class EventsService {

  private IBrokerPublisher brokerPublisher;

  public void sentEvents(final Event event) {
    brokerPublisher.sendMessageToBroker(Event.builder()
        .headers(event.getHeaders())
        .payload(new String(Base64.getDecoder().decode(event.getPayload())))
        .destinations(event.getDestinations())
        .build());
  }
}
