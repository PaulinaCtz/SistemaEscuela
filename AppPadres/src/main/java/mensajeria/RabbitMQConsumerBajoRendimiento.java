/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package mensajeria;

import app.frmChat;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import entidad.Mensaje;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 *
 * @author jvale
 */
public class RabbitMQConsumerBajoRendimiento implements Runnable {

    private final static String QUEUE_NAME = "cola_notificaciones";

    public RabbitMQConsumerBajoRendimiento() {
    }

    @Override
    public void run() {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");

        try {
            Connection connection = factory.newConnection();
            Channel channel = connection.createChannel();

            channel.queueDeclare(QUEUE_NAME, false, false, false, null);

            Consumer consumer = new DefaultConsumer(channel) {
                @Override
                public void handleDelivery(
                        String consumerTag,
                        Envelope envelope,
                        AMQP.BasicProperties properties,
                        byte[] body) throws IOException {

                    String destinatario = properties.getHeaders().get("destinatario").toString();

                    try {
                        // Descifrar el mensaje
                        byte[] mensajeDescifrado = decrypt(body);
                        String tarea = new String(mensajeDescifrado, StandardCharsets.UTF_8);

                        frmChat.textAreaNotificaciones.append(tarea + "\n");
                        System.out.println("Mensaje descifrado: " + tarea);
                        System.out.println("Destinatario: " + destinatario);

                        // Realizar cualquier acción con la tarea y el destinatario
                        // ...
                    } catch (GeneralSecurityException e) {
                        e.printStackTrace();
                        throw new IOException("Error al descifrar el mensaje", e);
                    }
                }
            };

            channel.basicConsume(QUEUE_NAME, true, consumer);
        } catch (IOException | TimeoutException e) {
            e.printStackTrace();
        }
    }

    private static byte[] decrypt(byte[] encryptedMessage) throws GeneralSecurityException {
        // Generar una clave secreta basada en una contraseña
        String password = "12345";
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] key = md.digest(password.getBytes(StandardCharsets.UTF_8));
        key = Arrays.copyOf(key, 16);
        SecretKey secretKey = new SecretKeySpec(key, "AES");

        // Crear un cifrador AES en modo de descifrado
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, secretKey);

        // Descifrar los bytes del mensaje
        return cipher.doFinal(encryptedMessage);
    }

}
