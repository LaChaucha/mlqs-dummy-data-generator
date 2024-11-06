package com.waterworks.mlqsdummydatagenerator.infra.httpout.image.api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.springframework.stereotype.Repository;

@Repository
public class RandomImageRepo {

  public byte[] consumeBinaryAPI() throws IOException {
    // Crear la URL de la API
    final URL url = new URL("https://random.imagecdn.app/500/500");

    // Abrir una conexión HTTP
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setRequestMethod("GET");

    // Verificar si la respuesta es exitosa
    if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
      // Obtener el flujo de entrada de la respuesta
      InputStream inputStream = connection.getInputStream();
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

      // Leer los datos binarios de la respuesta y almacenarlos en un byte array
      byte[] buffer = new byte[1024];
      int bytesRead;
      while ((bytesRead = inputStream.read(buffer)) != -1) {
        outputStream.write(buffer, 0, bytesRead);
      }

      // Cerrar los flujos
      outputStream.close();
      inputStream.close();

      // Retornar los datos binarios como un byte array
      return outputStream.toByteArray();
    } else {
      // En caso de que la respuesta no sea exitosa, lanzar una excepción
      throw new IOException(
          "Failed to fetch binary data from API. Response code: " + connection.getResponseCode());
    }
  }
}
