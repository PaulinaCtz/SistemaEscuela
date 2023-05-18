package not;

/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
/**
 *
 * @author Paulina Cortez Alamilla.
 */
import com.rabbitmq.client.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class Notificaciones {

    private final String EXCHANGE_NAME = "notificaciones";
    private final String QUEUE_NAME = "cola_notificaciones";//Cola tareas Pendientes

    public void enviarNotificacion(String tarea, String destinatario) throws IOException, GeneralSecurityException {
        // Encriptar el texto plano
        byte[] tareaEncriptada;
        tareaEncriptada = encrypt(tarea); // Manejar el error al encriptar el mensaje

        // P A R T E       D O S
        // Enviar notificación a la aplicación a través de RabbitMQ
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        try (Connection connection = factory.newConnection(); Channel channel = connection.createChannel()) {

            // Declarar exchange y cola
            channel.exchangeDeclare(EXCHANGE_NAME, "direct");
            channel.queueDeclare(QUEUE_NAME, false, false, false, null);
            channel.queueBind(QUEUE_NAME, EXCHANGE_NAME, "");

            // Crear headers con información adicional
            AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                    .headers(java.util.Map.of(
                            "destinatario", destinatario
                    ))
                    .build();

            // Publicar mensaje en el exchange
            channel.basicPublish(EXCHANGE_NAME, "", props, tareaEncriptada);
            System.out.println("Mensaje encriptado enviado: " + Base64.getEncoder().encodeToString(tareaEncriptada));

        } catch (Exception e) {
            System.out.print("aquí falla");
            e.printStackTrace();
        }
    }

    public void comunicacionModulo() throws IOException, TimeoutException {
        try {
            // Crear una conexión y un canal a RabbitMQ
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost("localhost");
            Connection connection = factory.newConnection();
            Channel channel = connection.createChannel();

            // Declarar la cola desde la que se consumirán los mensajes
            String cola = "envio_modulo_notificaciones"; // Reemplaza con el nombre de tu cola
            channel.queueDeclare(cola, true, false, false, null);

            // Crear un consumidor
            Consumer consumer = new DefaultConsumer(channel) {
                @Override
                public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws UnsupportedEncodingException, IOException {
                    try {
                        // Obtener el mensaje encriptado
                        byte[] mensajeEncriptado = body;

                        // Descifrar el mensaje
                        byte[] mensajeBytes = decrypt(mensajeEncriptado);

                        // Convertir el mensaje descifrado a texto plano
                        String mensajeRecibido = new String(mensajeBytes, "UTF-8");
                        System.out.println("Mensaje recibido de la cola: " + mensajeRecibido);

                        String destinatario = properties.getHeaders().get("destinatario").toString();

                        // Enviar notificación con el texto plano
                        enviarNotificacion(mensajeRecibido, destinatario);
                    } catch (GeneralSecurityException e) {
                        e.printStackTrace();
                        // Manejar el error al descifrar el mensaje
                    }
                }
            };

            // Registrar el consumidor en el canal
            channel.basicConsume(cola, true, consumer);

            // Mantener el consumidor activo
            while (true) {
                // El consumidor se mantiene activo indefinidamente
            }
        } catch (IOException | TimeoutException e) {
            e.printStackTrace();
            // Manejar la excepción de conexión con RabbitMQ
        }
    }

    private static byte[] decrypt(byte[] encryptedData) throws GeneralSecurityException, UnsupportedEncodingException {
        // Generar una clave secreta basada en una contraseña
        String password = "12345";
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] key = md.digest(password.getBytes("UTF-8"));
        key = Arrays.copyOf(key, 16);
        SecretKey secretKey = new SecretKeySpec(key, "AES");

        // Crear un cifrador AES en modo de descifrado
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, secretKey);

        // Descifrar los bytes del mensaje encriptado
        return cipher.doFinal(encryptedData);
    }

    public void consumirYReenviarMensaje() {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");

        try (Connection connection = factory.newConnection(); Channel channel = connection.createChannel()) {
            String colaOrigen = "notificacion_desde_app";
            String colaDestino = "cola_regreso_validacion";

            // Declarar la cola de origen
            channel.queueDeclare(colaOrigen, false, false, false, null);

            // Declarar la cola de destino
            channel.queueDeclare(colaDestino, false, false, false, null);

            // Crear consumer y procesar mensajes
            Consumer consumer = new DefaultConsumer(channel) {
                @Override
                public void handleDelivery(String consumerTag, Envelope envelope,
                        AMQP.BasicProperties properties, byte[] body) throws IOException, UnsupportedEncodingException {
                    // Descifrar el mensaje
                    byte[] decryptedMessage = null;
                    try {
                        decryptedMessage = decrypt(body);
                    } catch (GeneralSecurityException ex) {
                        Logger.getLogger(Notificaciones.class.getName()).log(Level.SEVERE, null, ex);
                    }

                    // Convertir los bytes descifrados a texto plano
                    String mensaje = deserialize(decryptedMessage);

                    System.out.println("Mensaje recibido desde 'notificacion_desde_app': " + mensaje);

                    // Publicar el mensaje en la cola de destino
                    try {
                        publicarMensaje(colaDestino, mensaje);
                    } catch (TimeoutException ex) {
                        Logger.getLogger(Notificaciones.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (GeneralSecurityException ex) {
                        Logger.getLogger(Notificaciones.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            };

            // Comenzar a consumir mensajes de la cola de origen
            while (true) {
                channel.basicConsume(colaOrigen, true, consumer);
            }
        } catch (Exception e) {
            System.out.print("Error al consumir y reenviar mensajes");
            e.printStackTrace();
        }
    }

    public void publicarMensaje(String cola, String mensaje) throws IOException, TimeoutException, GeneralSecurityException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");

        try (Connection connection = factory.newConnection(); Channel channel = connection.createChannel()) {
            channel.queueDeclare(cola, false, false, false, null);

            // Encriptar el mensaje
            byte[] encryptedMessage = encrypt(mensaje);

            // Publicar el mensaje encriptado
            channel.basicPublish("", cola, null, encryptedMessage);
            System.out.println("Mensaje encriptado publicado en la cola '" + cola + "'");
        }
    }

    private static byte[] encrypt(String mensaje) throws IOException, GeneralSecurityException {
        // Generar una clave secreta basada en una contraseña
        String password = "12345";
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] key = md.digest(password.getBytes(StandardCharsets.UTF_8));
        key = Arrays.copyOf(key, 16);
        SecretKey secretKey = new SecretKeySpec(key, "AES");

        // Crear un cifrador AES en modo de cifrado
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);

        // Convertir el mensaje a bytes
        byte[] mensajeBytes = mensaje.getBytes(StandardCharsets.UTF_8);

        // Encriptar los bytes del mensaje
        return cipher.doFinal(mensajeBytes);
    }

    private static String deserialize(byte[] messageBytes) throws IOException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(messageBytes); ObjectInput in = new ObjectInputStream(bis)) {
            return (String) in.readObject();
        } catch (ClassNotFoundException e) {
            throw new IOException("Error al deserializar el mensaje", e);
        }
    }

}
