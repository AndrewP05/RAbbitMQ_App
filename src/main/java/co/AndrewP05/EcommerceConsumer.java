package co.AndrewP05;

import com.rabbitmq.client.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class EcommerceConsumer {
    private static final String HOST = "localhost";
    private static final String PURCHASE_DIRECT_EXCHANGE = "compra_directa";
    private static final String PRODUCTS_FANOUT_EXCHANGE = "productos_fanout";
    private static final String OFFERS_FANOUT_EXCHANGE = "ofertas_fanout";
    private static final String QUEUE_PURCHASES = "cola_compras";

    private JFrame frame;
    private DefaultListModel<String> listModelProductos;
    private JList<String> listProductos;
    private JTextArea txtDetallesProducto;
    private JComboBox<String> cbCantidad;
    private JTextArea txtOfertas;
    private JTextArea logArea;
    private JTextField txtNombreCliente;

    private final Map<String, Map<String, String>> productos = new LinkedHashMap<>();
    private final Map<String, Integer> stockActual = new HashMap<>();
    private Channel channel;
    private String queueProductos;
    private String queueOffers;

    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            try {
                EcommerceConsumer consumer = new EcommerceConsumer();
                consumer.frame.setVisible(true);
                consumer.setupRabbitMQ();
                consumer.iniciarConsumidores();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public EcommerceConsumer() {
        initialize();
    }

    private void initialize() {
        frame = new JFrame("Sistema de E-commerce - Consumer");
        frame.setBounds(100, 100, 900, 700);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().setLayout(new BorderLayout(10, 10));

        // Panel Productos
        JPanel panelIzq = new JPanel(new BorderLayout());
        panelIzq.setBorder(BorderFactory.createTitledBorder("Productos Disponibles"));
        listModelProductos = new DefaultListModel<>();
        listProductos = new JList<>(listModelProductos);
        listProductos.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        listProductos.addListSelectionListener(evt -> mostrarDetallesProducto());
        panelIzq.add(new JScrollPane(listProductos), BorderLayout.CENTER);
        frame.getContentPane().add(panelIzq, BorderLayout.WEST);

        // Panel Detalles y Compra
        JPanel panelCentro = new JPanel(new BorderLayout());
        txtDetallesProducto = new JTextArea(10, 30);
        txtDetallesProducto.setEditable(false);
        txtDetallesProducto.setLineWrap(true);
        txtDetallesProducto.setWrapStyleWord(true);
        panelCentro.add(new JScrollPane(txtDetallesProducto), BorderLayout.CENTER);

        JPanel panelCompra = new JPanel(new GridLayout(0, 2, 10, 10));
        panelCompra.setBorder(BorderFactory.createTitledBorder("Datos de Compra"));
        panelCompra.add(new JLabel("Nombre del Cliente:"));
        txtNombreCliente = new JTextField(); panelCompra.add(txtNombreCliente);
        panelCompra.add(new JLabel("Cantidad a comprar:"));
        cbCantidad = new JComboBox<>(); panelCompra.add(cbCantidad);
        JButton btnComprar = new JButton("Comprar Producto");
        btnComprar.addActionListener(this::comprarProducto);
        panelCompra.add(btnComprar);
        panelCentro.add(panelCompra, BorderLayout.SOUTH);
        frame.getContentPane().add(panelCentro, BorderLayout.CENTER);

        // Panel Ofertas
        JPanel panelDer = new JPanel(new BorderLayout());
        panelDer.setBorder(BorderFactory.createTitledBorder("Ofertas"));
        txtOfertas = new JTextArea(10, 20);
        txtOfertas.setEditable(false);
        panelDer.add(new JScrollPane(txtOfertas), BorderLayout.CENTER);
        frame.getContentPane().add(panelDer, BorderLayout.EAST);

        // Log
        logArea = new JTextArea();
        logArea.setEditable(false);
        frame.getContentPane().add(new JScrollPane(logArea), BorderLayout.SOUTH);
    }

    private void setupRabbitMQ() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(HOST);
        Connection connection = factory.newConnection();
        channel = connection.createChannel();

        // Exchange compras
        channel.exchangeDeclare(PURCHASE_DIRECT_EXCHANGE, BuiltinExchangeType.DIRECT, true);
        channel.queueDeclare(QUEUE_PURCHASES, true, false, false, null);
        channel.queueBind(QUEUE_PURCHASES, PURCHASE_DIRECT_EXCHANGE, "compra");
        System.out.println("[Consumer] Cola compras: " + QUEUE_PURCHASES);

        // Exchange productos fanout
        channel.exchangeDeclare(PRODUCTS_FANOUT_EXCHANGE, BuiltinExchangeType.FANOUT, true);
        queueProductos = channel.queueDeclare().getQueue();
        channel.queueBind(queueProductos, PRODUCTS_FANOUT_EXCHANGE, "");
        System.out.println("[Consumer] Cola productos (anónima): " + queueProductos);

        // Exchange ofertas fanout
        channel.exchangeDeclare(OFFERS_FANOUT_EXCHANGE, BuiltinExchangeType.FANOUT, true);
        queueOffers = channel.queueDeclare().getQueue();
        channel.queueBind(queueOffers, OFFERS_FANOUT_EXCHANGE, "");
        System.out.println("[Consumer] Cola ofertas (anónima): " + queueOffers);

        log("RabbitMQ configurado: compras, productos y ofertas listos");
    }

    private void iniciarConsumidores() {
        try {
            // Consumidor compras
            channel.basicConsume(QUEUE_PURCHASES, true, new DefaultConsumer(channel) {
                @Override public void handleDelivery(String consumerTag, Envelope envelope,
                                                      AMQP.BasicProperties props, byte[] body) throws IOException {
                    String compra = new String(body, StandardCharsets.UTF_8);
                    System.out.println("[Compra] " + compra);
                    SwingUtilities.invokeLater(() -> log("Compra registrada: " + compra));
                }
            });

            // Consumidor productos
            channel.basicConsume(queueProductos, true, new DefaultConsumer(channel) {
                @Override public void handleDelivery(String consumerTag, Envelope envelope,
                                                      AMQP.BasicProperties props, byte[] body) throws IOException {
                    String msg = new String(body, StandardCharsets.UTF_8);
                    System.out.println("[Producto] " + msg);
                    SwingUtilities.invokeLater(() -> procesarNuevoProducto(msg));
                }
            });

            // Consumidor ofertas
            channel.basicConsume(queueOffers, true, new DefaultConsumer(channel) {
                @Override public void handleDelivery(String consumerTag, Envelope envelope,
                                                      AMQP.BasicProperties props, byte[] body) throws IOException {
                    String oferta = new String(body, StandardCharsets.UTF_8);
                    System.out.println("[Oferta] " + oferta);
                    SwingUtilities.invokeLater(() -> txtOfertas.append("Oferta recibida: " + oferta + "\n"));
                }
            });

            log("Consumidores iniciados. Esperando compras, productos y ofertas...");
        } catch (IOException e) {
            log("Error iniciando consumidores: " + e.getMessage());
            System.err.println("[Consumer] Error: " + e.getMessage());
        }
    }

    private void procesarNuevoProducto(String message) {
        Map<String, String> productoMap = stringToMap(message);
        String nombre = productoMap.get("nombre");
        if (nombre != null) {
            productos.put(nombre, productoMap);
            int stock = Integer.parseInt(productoMap.getOrDefault("stock", "0"));
            stockActual.put(nombre, stock);
            SwingUtilities.invokeLater(() -> {
                if (!listModelProductos.contains(nombre)) listModelProductos.addElement(nombre);
                log("Producto actualizado: " + nombre);
            });
        }
    }

    private void mostrarDetallesProducto() {
        String nombre = listProductos.getSelectedValue();
        if (nombre != null && productos.containsKey(nombre)) {
            Map<String, String> p = productos.get(nombre);
            int stock = stockActual.getOrDefault(nombre, 0);
            String det = String.format(
                "Nombre: %s\nCategoría: %s\nFecha: %s\nMarca: %s\nSección: %s\nPrecio: $%s\nStock: %d",
                nombre, p.get("categoria"), p.get("fecha_publicacion"),
                p.get("marca"), p.get("seccion"), p.get("precio"), stock);
            txtDetallesProducto.setText(det);
            cbCantidad.removeAllItems();
            for (int i = 1; i <= stock; i++) cbCantidad.addItem(String.valueOf(i));
        }
    }

    private void comprarProducto(ActionEvent e) {
        String nombre = listProductos.getSelectedValue();
        String cantidad = (String) cbCantidad.getSelectedItem();
        String cliente = txtNombreCliente.getText();
        if (nombre == null || cantidad == null || cliente.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Complete todos los campos", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            String msg = nombre + ":" + cantidad + ";cliente:" + cliente;
            channel.basicPublish(PURCHASE_DIRECT_EXCHANGE, "compra", null, msg.getBytes(StandardCharsets.UTF_8));
            System.out.println("[Enviado Compra] " + msg);
            int restante = stockActual.get(nombre) - Integer.parseInt(cantidad);
            stockActual.put(nombre, restante);
            mostrarDetallesProducto();
            txtNombreCliente.setText("");
            log("Compra enviada: " + msg);
        } catch (IOException ex) {
            log("Error al enviar compra: " + ex.getMessage());
            System.err.println("[ERROR Enviar Compra] " + ex.getMessage());
        }
    }

    private Map<String, String> stringToMap(String str) {
        Map<String, String> map = new HashMap<>();
        for (String entry : str.split(";")) {
            String[] kv = entry.split(":");
            if (kv.length == 2) map.put(kv[0], kv[1]);
        }
        return map;
    }

    private void log(String message) {
        logArea.append(message + "\n");
    }
}
