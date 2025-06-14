package co.AndrewP05;

import com.rabbitmq.client.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class EcommerceProducer {
    private static final String HOST = "localhost";
    private static final String PURCHASE_DIRECT_EXCHANGE = "compra_directa";
    private static final String PRODUCTS_FANOUT_EXCHANGE = "productos_fanout";
    private static final String OFFERS_FANOUT_EXCHANGE = "ofertas_fanout";
    private static final String QUEUE_PURCHASES = "cola_compras";
    private static final String QUEUE_OFFERS = "cola_ofertas";

    private JFrame frame;
    private JTextField txtName, txtDate, txtBrand, txtPrice, txtStock;
    private JComboBox<String> cbCategory, cbSection;
    private JTextArea txtOffer;
    private JTextArea logArea;

    private Connection connection;
    private Channel channel;

    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            try {
                EcommerceProducer producer = new EcommerceProducer();
                producer.frame.setVisible(true);
                producer.setupRabbitMQ();
                producer.startPurchaseConsumer();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public EcommerceProducer() {
        initializeUI();
    }

    private void initializeUI() {
        frame = new JFrame("E-commerce Producer");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setLayout(new BorderLayout(10,10));

        JPanel panel = new JPanel(new GridLayout(0,2,10,10));
        panel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        frame.add(panel, BorderLayout.NORTH);

        panel.add(new JLabel("Nombre del Producto:")); txtName = new JTextField(); panel.add(txtName);
        panel.add(new JLabel("Categoría:")); cbCategory = new JComboBox<>(new String[]{"Tecnología","Ropa","Hogar","Juguetes"}); panel.add(cbCategory);
        panel.add(new JLabel("Fecha de Publicación:")); txtDate = new JTextField(); panel.add(txtDate);
        panel.add(new JLabel("Marca/Descripción:")); txtBrand = new JTextField(); panel.add(txtBrand);
        panel.add(new JLabel("Sección:")); cbSection = new JComboBox<>(new String[]{"General","Ofertas","Premium","Outlet"}); panel.add(cbSection);
        panel.add(new JLabel("Precio:")); txtPrice = new JTextField(); panel.add(txtPrice);
        panel.add(new JLabel("Stock Disponible:")); txtStock = new JTextField(); panel.add(txtStock);

        JButton btnPublish = new JButton("Publicar Producto");
        btnPublish.addActionListener(this::publishProduct);
        panel.add(btnPublish);

        JButton btnPublishOffer = new JButton("Publicar Oferta");
        btnPublishOffer.addActionListener(this::publishOffer);
        panel.add(btnPublishOffer);

        logArea = new JTextArea(); logArea.setEditable(false);
        frame.add(new JScrollPane(logArea), BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout());
        south.setBorder(BorderFactory.createTitledBorder("Oferta Global"));
        txtOffer = new JTextArea(3,20);
        south.add(new JScrollPane(txtOffer), BorderLayout.CENTER);
        frame.add(south, BorderLayout.SOUTH);
    }

    private void setupRabbitMQ() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(HOST);
        connection = factory.newConnection();
        channel = connection.createChannel();

        // Declara exchanges
        channel.exchangeDeclare(PURCHASE_DIRECT_EXCHANGE, BuiltinExchangeType.DIRECT, true);
        channel.exchangeDeclare(PRODUCTS_FANOUT_EXCHANGE, BuiltinExchangeType.FANOUT, true);
        channel.exchangeDeclare(OFFERS_FANOUT_EXCHANGE, BuiltinExchangeType.FANOUT, true);

        // Cola de compras (direct)
        channel.queueDeclare(QUEUE_PURCHASES, true, false, false, null);
        channel.queueBind(QUEUE_PURCHASES, PURCHASE_DIRECT_EXCHANGE, "compra");

        // Cola de ofertas (fanout)
        channel.queueDeclare(QUEUE_OFFERS, true, false, false, null);
        channel.queueBind(QUEUE_OFFERS, OFFERS_FANOUT_EXCHANGE, "");

        log("RabbitMQ setup: exchanges y colas declarados");
        System.out.println("[Producer] RabbitMQ initialized: exchanges and queues declared");
    }

    private void startPurchaseConsumer() {
        try {
            channel.basicConsume(QUEUE_PURCHASES, true, new DefaultConsumer(channel) {
                @Override public void handleDelivery(String tag, Envelope env, AMQP.BasicProperties props, byte[] body) throws IOException {
                    String compra = new String(body, StandardCharsets.UTF_8);
                    System.out.println("[Producer Consumer] Compra recibida en producer: " + compra);
                    SwingUtilities.invokeLater(() -> log("Compra recibida: " + compra));
                }
            });
        } catch (IOException e) {
            log("Error al consumir compras: "+e.getMessage());
            System.err.println("[Producer Consumer] Error consuming purchases: " + e.getMessage());
        }
    }

    private void publishProduct(ActionEvent e) {
        try {
            String nombre = txtName.getText();
            String categoria = cbCategory.getSelectedItem().toString();
            String fecha = txtDate.getText();
            String marca = txtBrand.getText();
            String seccion = cbSection.getSelectedItem().toString();
            String precio = txtPrice.getText();
            String stock = txtStock.getText();
            if(nombre.isEmpty()||fecha.isEmpty()||marca.isEmpty()||precio.isEmpty()||stock.isEmpty()){
                JOptionPane.showMessageDialog(frame,"Todos los campos son obligatorios","Error",JOptionPane.ERROR_MESSAGE);
                return;
            }
            Map<String,String> prod = new LinkedHashMap<>();
            prod.put("nombre",nombre);
            prod.put("categoria",categoria);
            prod.put("fecha_publicacion",fecha);
            prod.put("marca",marca);
            prod.put("seccion",seccion);
            prod.put("precio",precio);
            prod.put("stock",stock);
            String msg = mapToString(prod);

            // Publicar en exchange fanout de productos
            channel.basicPublish(PRODUCTS_FANOUT_EXCHANGE, "", null, msg.getBytes(StandardCharsets.UTF_8));
            log("Producto publicado: " + nombre);
            System.out.println(String.format("[Producer] Enviado a exchange '%s': %s",
                PRODUCTS_FANOUT_EXCHANGE, msg));
        } catch (Exception ex) {
            log("Error al publicar producto: "+ex.getMessage());
            System.err.println("[Producer] Error publishing product: " + ex.getMessage());
        }
    }

    private void publishOffer(ActionEvent e) {
        String oferta = txtOffer.getText();
        if(oferta.isEmpty()){
            JOptionPane.showMessageDialog(frame,"La oferta no puede estar vacía","Error",JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            channel.basicPublish(OFFERS_FANOUT_EXCHANGE, "", null, oferta.getBytes(StandardCharsets.UTF_8));
            log("Oferta enviada: " + oferta);
            System.out.println(String.format("[Producer] Enviado a exchange '%s': %s",
                OFFERS_FANOUT_EXCHANGE, oferta));
            txtOffer.setText("");
        } catch (IOException ex) {
            log("Error al publicar oferta: "+ex.getMessage());
            System.err.println("[Producer] Error publishing offer: " + ex.getMessage());
        }
    }

    private String mapToString(Map<String,String> map) {
        StringBuilder sb = new StringBuilder();
        map.forEach((k,v)-> sb.append(k).append(":").append(v).append(";"));
        return sb.toString();
    }

    private void log(String msg) {
        logArea.append(msg+"\n");
    }
}
