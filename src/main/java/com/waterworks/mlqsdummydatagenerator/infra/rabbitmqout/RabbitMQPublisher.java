package com.waterworks.mlqsdummydatagenerator.infra.rabbitmqout;

import com.waterworks.mlqsdummydatagenerator.app.events.domain.Event;
import com.waterworks.mlqsdummydatagenerator.app.events.spi.IBrokerPublisher;
import lombok.AllArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Repository;

/**
 * The `RabbitMQPublisher` class is a component responsible for publishing events to a RabbitMQ
 * message broker.
 * <p>
 * This class is annotated with `@AllArgsConstructor` to enable constructor-based dependency
 * injection and `@Repository` to indicate that it's a repository component.
 * </p>
 *
 * @author Edgar Thomson
 * @version 1.0
 */
@AllArgsConstructor
@Repository
public class RabbitMQPublisher implements IBrokerPublisher {

  private RabbitTemplate rabbitTemplate;

  /**
   * Publishes an event to the RabbitMQ message broker for further distribution.
   *
   * @param event The `Event` object representing the event to be published.
   */
  @Override
  public void sendMessageToBroker(final Event event) {

    event.getDestinations().forEach(
        destination -> rabbitTemplate.convertAndSend(destination, "", event.getPayload(),
            message -> {
              message.getMessageProperties().setHeaders(event.getHeaders());
              if (event.getHeaders().containsKey("content_type")) {
                message.getMessageProperties()
                    .setContentType(String.valueOf(event.getHeaders().get("content_type")));
              } else {
                message.getMessageProperties().setContentType("application/json");
              }
              return message;
            }));
  }
}
